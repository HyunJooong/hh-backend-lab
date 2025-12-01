package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetTopProductsBySalesUseCase {

    private final ProductRepository productRepository;

    @Qualifier("productCacheTemplate")
    private final RedisTemplate<String, List<Product>> redisTemplate;

    //  1시간 30분: 스케줄러보다 여유있게 설정 (만료 전에 갱신 보장)
    private static final int CACHE_TTL_MINUTES = 90;

    //  미리 캐싱할 인기 상품 개수 (TOP 10, 20, 50)
    private static final int[] DEFAULT_LIMITS = {10, 20, 50};

    /**
     * 애플리케이션 시작 시 캐시 워밍업 (초기화)
     * DEFAULT_LIMITS에 해당하는 캐시를 미리 생성
     */
    @PostConstruct
    public void warmUpCache() {
        log.info("===== 인기 상품 캐시 워밍업 시작 =====");
        refreshTopProductsCache();
    }

    /**
     * 최근 7일간 판매량 기준 인기 상품을 조회합니다.
     * 캐시 우선 조회 → 스케줄러가 미리 준비한 데이터 반환
     *
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록 (최근 7일간 판매량 내림차순)
     */
    @Transactional(readOnly = true)
    public List<Product> getTopProductsBySales(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("조회할 상품 개수는 1개 이상이어야 합니다.");
        }

        // 동적 limit 처리: 가장 가까운 DEFAULT_LIMIT으로 매핑
        int mappedLimit = mapToDefaultLimit(limit);
        String cacheKey = buildCacheKey(mappedLimit);

        // 캐시에서 조회
        List<Product> cachedProducts = redisTemplate.opsForValue().get(cacheKey);
        if (cachedProducts != null) {
            log.debug("캐시 히트 - 인기 상품 조회 성공: 요청 limit={}, 캐시 limit={}",
                    limit, mappedLimit);

            // 요청한 limit만큼만 반환 (subList)
            return cachedProducts.size() > limit ?
                    cachedProducts.subList(0, limit) : cachedProducts;
        }

        // 캐시 미스 (거의 발생하지 않음)
        log.warn("캐시 미스 발생! 즉시 DB 조회: limit={} " +
                "(스케줄러가 정상 작동하지 않을 수 있습니다)", limit);
        return fetchAndCacheTopProducts(mappedLimit);
    }

    /**
     * 요청 limit을 가장 가까운 DEFAULT_LIMIT으로 매핑
     * 예: limit=15 → 20, limit=35 → 50
     *
     * @param requestedLimit 요청한 상품 개수
     * @return 매핑된 DEFAULT_LIMIT
     */
    private int mapToDefaultLimit(int requestedLimit) {
        for (int defaultLimit : DEFAULT_LIMITS) {
            if (requestedLimit <= defaultLimit) {
                return defaultLimit;
            }
        }
        // 최대 DEFAULT_LIMIT보다 큰 경우, 가장 큰 값 반환
        return DEFAULT_LIMITS[DEFAULT_LIMITS.length - 1];
    }

    /**
     * 스케줄러: 매 1시간마다 인기 상품 캐시 갱신
     * cron: "0 0 * * * *" = 매 시 정각 (00분 00초)
     *
     * 예: 09:00:00, 10:00:00, 11:00:00, ...
     */
    @Scheduled(cron = "0 0 * * * *")
    public void refreshTopProductsCache() {
        log.info("===== 인기 상품 캐시 갱신 시작 =====");
        long startTime = System.currentTimeMillis();

        int successCount = 0;
        int failCount = 0;

        for (int limit : DEFAULT_LIMITS) {
            try {
                List<Product> products = fetchAndCacheTopProducts(limit);
                successCount++;
                log.info(" 인기 상품 캐시 갱신 완료: limit={}, 조회된 상품 수={}",
                        limit, products.size());
            } catch (Exception e) {
                failCount++;
                log.error(" 인기 상품 캐시 갱신 실패: limit={}", limit, e);
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("===== 인기 상품 캐시 갱신 완료 ===== " +
                        "성공: {}, 실패: {}, 소요시간: {}ms",
                successCount, failCount, elapsedTime);
    }

    /**
     * DB에서 조회하고 Redis에 캐싱
     *
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록
     */
    @Transactional(readOnly = true)
    protected List<Product> fetchAndCacheTopProducts(int limit) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        Pageable pageable = PageRequest.of(0, limit);

        List<Product> products = productRepository.findTopProductsBySalesInLastWeek(
                weekAgo, pageable);

        // Redis에 저장 (1시간 30분 TTL)
        String cacheKey = buildCacheKey(limit);
        redisTemplate.opsForValue().set(
                cacheKey,
                products,
                CACHE_TTL_MINUTES,
                TimeUnit.MINUTES);

        log.debug("Redis 캐시 저장 완료: key={}, 상품 수={}, TTL={}분",
                cacheKey, products.size(), CACHE_TTL_MINUTES);

        return products;
    }

    /**
     * 캐시 키 생성
     *
     * @param limit 조회할 상품 개수
     * @return Redis 캐시 키
     */
    private String buildCacheKey(int limit) {
        return "products:top:sales:7days:limit:" + limit;
    }

    /**
     * 캐시 수동 무효화 및 즉시 갱신
     * 특별한 이벤트나 대량 주문 발생 시 수동으로 호출 가능
     */
    public void forceRefreshCache() {
        log.info("인기 상품 캐시 강제 갱신 시작");
        refreshTopProductsCache();
    }

    /**
     * 특정 limit에 대한 캐시만 갱신
     *
     * @param limit 갱신할 상품 개수
     */
    public void refreshCacheForLimit(int limit) {
        try {
            List<Product> products = fetchAndCacheTopProducts(limit);
            log.info("특정 limit 캐시 갱신 완료: limit={}, 상품 수={}",
                    limit, products.size());
        } catch (Exception e) {
            log.error("특정 limit 캐시 갱신 실패: limit={}", limit, e);
            throw e;
        }
    }
}