package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis Sorted Set을 활용한 주간 판매량 기준 인기 상품 조회 서비스
 *
 * 주요 기능:
 * 1. Redis Sorted Set에 상품ID를 저장하고 판매량을 스코어로 관리
 * 2. TTL 7일 설정으로 주간 랭킹 자동 만료
 * 3. 주문 발생 시 실시간 판매량 증가
 * 4. 스케줄러로 매일 자정 DB와 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetTopProductsBySalesUseCase {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, String> redisTemplate;

    // Redis Sorted Set 키: 주간 판매량 랭킹
    private static final String WEEKLY_RANKING_KEY = "products:ranking:weekly:sales";

    // TTL: 7일 (주간 랭킹은 일주일 후 만료)
    private static final long WEEKLY_TTL_DAYS = 7;

    /**
     * 애플리케이션 시작 시 Redis 랭킹 초기화
     */
    @PostConstruct
    public void warmUpCache() {
        log.info("===== 주간 판매량 랭킹 초기화 시작 =====");
        syncRankingFromDatabase();
    }

    /**
     * 최근 7일간 판매량 기준 인기 상품을 조회합니다.
     * Redis Sorted Set에서 조회 (스코어 높은 순)
     *
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록 (최근 7일간 판매량 내림차순)
     */
    @Transactional(readOnly = true)
    public List<Product> getTopProductsBySales(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("조회할 상품 개수는 1개 이상이어야 합니다.");
        }

        try {
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

            // Sorted Set에서 스코어 높은 순으로 조회 (ZREVRANGE)
            Set<String> topProductIds = zSetOps.reverseRange(WEEKLY_RANKING_KEY, 0, limit - 1);

            if (topProductIds == null || topProductIds.isEmpty()) {
                log.warn("Redis 랭킹 데이터 없음. DB 동기화 시도");
                syncRankingFromDatabase();
                topProductIds = zSetOps.reverseRange(WEEKLY_RANKING_KEY, 0, limit - 1);
            }

            if (topProductIds == null || topProductIds.isEmpty()) {
                log.warn("랭킹 데이터가 비어있습니다.");
                return new ArrayList<>();
            }

            // Product 엔티티 조회 (순서 유지)
            List<Product> products = new ArrayList<>();
            for (String productIdStr : topProductIds) {
                Long productId = Long.parseLong(productIdStr);
                productRepository.findById(productId).ifPresent(products::add);
            }

            log.debug("주간 판매량 TOP {} 조회 완료: {} 건", limit, products.size());
            return products;

        } catch (Exception e) {
            log.error("주간 판매량 조회 실패. DB로 폴백", e);
            return fallbackToDatabase(limit);
        }
    }

    /**
     * 주문 발생 시 특정 상품의 판매량을 실시간으로 증가
     *
     * @param productId 상품 ID
     * @param quantity 판매 수량
     */
    public void incrementSales(Long productId, int quantity) {
        try {
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

            // 판매량 증가 (ZINCRBY 연산)
            zSetOps.incrementScore(WEEKLY_RANKING_KEY, productId.toString(), quantity);

            // TTL 갱신 (7일)
            redisTemplate.expire(WEEKLY_RANKING_KEY, WEEKLY_TTL_DAYS, TimeUnit.DAYS);

            log.debug("판매량 증가: productId={}, quantity={}", productId, quantity);
        } catch (Exception e) {
            log.error("판매량 증가 실패: productId={}, quantity={}", productId, quantity, e);
            // Redis 오류가 주문 처리를 막지 않도록 예외를 던지지 않음
        }
    }

    /**
     * 스케줄러: 매일 자정에 DB 데이터와 동기화
     * cron: "0 0 0 * * *" = 매일 00:00:00
     *
     * Redis 데이터와 실제 DB 판매량을 일치시키기 위한 동기화 작업
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshTopProductsCache() {
        log.info("===== 주간 판매량 랭킹 DB 동기화 시작 (스케줄러) =====");
        syncRankingFromDatabase();
    }

    /**
     * DB에서 최근 7일 판매량 데이터를 조회하여 Redis Sorted Set 동기화
     */
    @Transactional(readOnly = true)
    protected void syncRankingFromDatabase() {
        long startTime = System.currentTimeMillis();

        try {
            LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
            Pageable pageable = PageRequest.of(0, 100); // TOP 100까지 동기화

            // DB에서 최근 7일간 판매량 조회
            List<Product> products = productRepository.findTopProductsBySalesInLastWeek(
                    weekAgo, pageable);

            if (products.isEmpty()) {
                log.warn("DB에 판매 데이터가 없습니다.");
                return;
            }

            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

            // 기존 랭킹 삭제
            redisTemplate.delete(WEEKLY_RANKING_KEY);

            // 새로운 랭킹 데이터 삽입
            for (Product product : products) {
                // 판매량 계산 (OrderItem에서 집계된 값 사용)
                Long salesCount = calculateSalesCount(product.getId(), weekAgo);

                if (salesCount > 0) {
                    zSetOps.add(WEEKLY_RANKING_KEY,
                               product.getId().toString(),
                               salesCount.doubleValue());
                }
            }

            // TTL 설정 (7일)
            redisTemplate.expire(WEEKLY_RANKING_KEY, WEEKLY_TTL_DAYS, TimeUnit.DAYS);

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("===== 주간 판매량 랭킹 DB 동기화 완료 ===== " +
                    "상품 수: {}, 소요시간: {}ms", products.size(), elapsedTime);

        } catch (Exception e) {
            log.error("주간 판매량 랭킹 DB 동기화 실패", e);
        }
    }

    /**
     * 특정 상품의 최근 7일간 판매량 계산
     * OrderItem 집계 쿼리로 실제 판매량을 계산
     *
     * @param productId 상품 ID
     * @param weekAgo 7일 전 시간
     * @return 판매량
     */
    @Transactional(readOnly = true)
    protected Long calculateSalesCount(Long productId, LocalDateTime weekAgo) {
        return productRepository.calculateSalesCountByProductId(productId, weekAgo);
    }

    /**
     * Redis 장애 시 DB로 폴백
     *
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록
     */
    @Transactional(readOnly = true)
    protected List<Product> fallbackToDatabase(int limit) {
        log.warn("Redis 폴백: DB에서 직접 조회");
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        Pageable pageable = PageRequest.of(0, limit);
        return productRepository.findTopProductsBySalesInLastWeek(weekAgo, pageable);
    }

    /**
     * 캐시 수동 무효화 및 즉시 갱신
     * 특별한 이벤트나 대량 주문 발생 시 수동으로 호출 가능
     */
    public void forceRefreshCache() {
        log.info("주간 판매량 랭킹 강제 갱신 시작");
        syncRankingFromDatabase();
    }

    /**
     * 특정 상품의 현재 판매량 스코어 조회
     *
     * @param productId 상품 ID
     * @return 판매량 (없으면 0)
     */
    public Double getProductSalesScore(Long productId) {
        try {
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
            Double score = zSetOps.score(WEEKLY_RANKING_KEY, productId.toString());
            return score != null ? score : 0.0;
        } catch (Exception e) {
            log.error("판매량 스코어 조회 실패: productId={}", productId, e);
            return 0.0;
        }
    }

    /**
     * 전체 랭킹 데이터 삭제 (테스트용)
     */
    public void clearRanking() {
        redisTemplate.delete(WEEKLY_RANKING_KEY);
        log.info("주간 판매량 랭킹 데이터 삭제 완료");
    }
}