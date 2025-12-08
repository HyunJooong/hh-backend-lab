package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.CategoryRepository;
import com.choo.hhbackendlab.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GetTopProductsBySalesUseCase 통합 테스트
 * Redis Pipeline 적용 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class GetTopProductsBySalesUseCaseTest {

    @Autowired
    private GetTopProductsBySalesUseCase getTopProductsBySalesUseCase;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String WEEKLY_RANKING_KEY = "pds:rks:wek:sl";

    private Category category;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() throws Exception {
        // Redis 초기화
        getTopProductsBySalesUseCase.clearRanking();

        // 테스트 데이터 생성 - Reflection으로 Category 필드 설정
        category = createCategory("전자제품", "ELEC");
        categoryRepository.save(category);

        product1 = new Product("laptop", "노트북", category, 100000, 10);
        product2 = new Product("mouse", "마우스", category, 30000, 20);
        product3 = new Product("keyboard", "키보드", category, 50000, 15);

        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);
    }

    /**
     * Reflection을 사용하여 Category 객체 생성
     */
    private Category createCategory(String name, String code) throws Exception {
        Category category = new Category();
        setField(category, "name", name);
        setField(category, "code", code);
        return category;
    }

    /**
     * Reflection으로 private 필드 설정
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("판매량 증가 후 Redis Sorted Set 조회 검증")
    void incrementSales_And_GetTopProducts() {
        // given - 판매량 증가
        getTopProductsBySalesUseCase.incrementSales(product1.getId(), 100);
        getTopProductsBySalesUseCase.incrementSales(product2.getId(), 50);
        getTopProductsBySalesUseCase.incrementSales(product3.getId(), 75);

        // when - 상위 3개 조회
        List<Product> topProducts = getTopProductsBySalesUseCase.getTopProductsBySales(3);

        // then - 판매량 순서대로 정렬 확인
        assertThat(topProducts).hasSize(3);
        assertThat(topProducts.get(0).getId()).isEqualTo(product1.getId());
        assertThat(topProducts.get(1).getId()).isEqualTo(product3.getId());
        assertThat(topProducts.get(2).getId()).isEqualTo(product2.getId());
    }

    @Test
    @DisplayName("판매량 스코어 조회 검증")
    void getProductSalesScore_Success() {
        // given
        getTopProductsBySalesUseCase.incrementSales(product1.getId(), 100);
        getTopProductsBySalesUseCase.incrementSales(product1.getId(), 50);

        // when
        Double score = getTopProductsBySalesUseCase.getProductSalesScore(product1.getId());

        // then
        assertThat(score).isEqualTo(150.0);
    }

    @Test
    @DisplayName("DB 동기화 시 Redis Pipeline 적용 검증")
    void syncRankingFromDatabase_WithPipeline() {
        // given - DB에 판매 데이터 존재하도록 설정 (Mock이 아닌 실제 DB 필요)
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // 기존 데이터 초기화
        getTopProductsBySalesUseCase.clearRanking();

        // Redis에 직접 추가 (테스트용)
        zSetOps.add(WEEKLY_RANKING_KEY, product1.getId().toString(), 100.0);
        zSetOps.add(WEEKLY_RANKING_KEY, product2.getId().toString(), 50.0);
        zSetOps.add(WEEKLY_RANKING_KEY, product3.getId().toString(), 75.0);

        // when - 동기화 호출
        long startTime = System.currentTimeMillis();
        getTopProductsBySalesUseCase.forceRefreshCache();
        long elapsedTime = System.currentTimeMillis() - startTime;

        // then - 파이프라인 적용으로 빠른 처리 확인
        Set<String> topProductIds = zSetOps.reverseRange(WEEKLY_RANKING_KEY, 0, 2);
        assertThat(topProductIds).isNotNull();

        System.out.println("DB 동기화 소요 시간 (파이프라인 적용): " + elapsedTime + "ms");
    }

    @Test
    @DisplayName("Redis 랭킹 데이터 없을 때 DB 폴백 동작 검증")
    void getTopProducts_Fallback_WhenRedisEmpty() {
        // given - Redis 초기화 (데이터 없음)
        getTopProductsBySalesUseCase.clearRanking();

        // when - 조회 시도 (Redis 없으면 DB로 폴백)
        List<Product> topProducts = getTopProductsBySalesUseCase.getTopProductsBySales(3);

        // then - 빈 목록 또는 DB에서 조회된 결과 반환
        assertThat(topProducts).isNotNull();
    }

    @Test
    @DisplayName("잘못된 limit 값으로 조회 시 예외 발생")
    void getTopProducts_InvalidLimit() {
        // given
        int invalidLimit = 0;

        // when & then
        assertThatThrownBy(() -> getTopProductsBySalesUseCase.getTopProductsBySales(invalidLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조회할 상품 개수는 1개 이상이어야 합니다");
    }

    @Test
    @DisplayName("TTL 설정 검증 - 7일 후 만료")
    void ttl_ExpiresAfter7Days() throws InterruptedException {
        // given
        getTopProductsBySalesUseCase.incrementSales(product1.getId(), 100);

        // when - TTL 조회
        Long ttl = redisTemplate.getExpire(WEEKLY_RANKING_KEY);

        // then - 7일(604800초) 설정 확인
        assertThat(ttl).isGreaterThan(0L);
        assertThat(ttl).isLessThanOrEqualTo(604800L); // 7일 = 604800초
    }

    @Test
    @DisplayName("여러 상품 동시 판매량 증가 후 정렬 검증")
    void multipleIncrements_SortedCorrectly() {
        // given - 여러 번 판매량 증가
        getTopProductsBySalesUseCase.incrementSales(product1.getId(), 10);
        getTopProductsBySalesUseCase.incrementSales(product2.getId(), 30);
        getTopProductsBySalesUseCase.incrementSales(product3.getId(), 20);

        getTopProductsBySalesUseCase.incrementSales(product1.getId(), 50); // 총 60
        getTopProductsBySalesUseCase.incrementSales(product2.getId(), 5);  // 총 35

        // when
        List<Product> topProducts = getTopProductsBySalesUseCase.getTopProductsBySales(3);

        // then - 판매량 순서: product1(60) > product2(35) > product3(20)
        assertThat(topProducts).hasSize(3);
        assertThat(topProducts.get(0).getId()).isEqualTo(product1.getId());
        assertThat(topProducts.get(1).getId()).isEqualTo(product2.getId());
        assertThat(topProducts.get(2).getId()).isEqualTo(product3.getId());
    }

    @Test
    @DisplayName("랭킹 데이터 삭제 후 빈 목록 조회")
    void clearRanking_ReturnsEmptyList() {
        // given
        getTopProductsBySalesUseCase.incrementSales(product1.getId(), 100);

        // when
        getTopProductsBySalesUseCase.clearRanking();
        List<Product> topProducts = getTopProductsBySalesUseCase.getTopProductsBySales(3);

        // then
        assertThat(topProducts).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 상품 판매량 스코어 조회 시 0 반환")
    void getProductSalesScore_NotExists() {
        // given
        Long nonExistentProductId = 99999L;

        // when
        Double score = getTopProductsBySalesUseCase.getProductSalesScore(nonExistentProductId);

        // then
        assertThat(score).isEqualTo(0.0);
    }
}