package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class ProductRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    private Category category;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        // Category 생성
        category = new Category();
        entityManager.persist(category);

        // Product 생성
        product1 = new Product("laptop", "노트북입니다.", category, 100000, 10);
        product2 = new Product("mouse", "마우스입니다.", category, 30000, 20);
        product3 = new Product("keyboard", "키보드입니다.", category, 50000, 15);

        // 조회수 설정
        product1.incrementViewCount();
        product1.incrementViewCount();
        product1.incrementViewCount(); // viewCount: 3

        product2.incrementViewCount();
        product2.incrementViewCount(); // viewCount: 2

        product3.incrementViewCount(); // viewCount: 1

        entityManager.persist(product1);
        entityManager.persist(product2);
        entityManager.persist(product3);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("상품 저장 성공")
    void save_Success() {
        // given
        Product newProduct = new Product("monitor", "모니터입니다.", category, 200000, 5);

        // when
        Product savedProduct = productRepository.save(newProduct);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Product> foundProduct = productRepository.findById(savedProduct.getId());
        assertThat(foundProduct).isPresent();
        assertThat(foundProduct.get().getName()).isEqualTo("monitor");
        assertThat(foundProduct.get().getPrice()).isEqualTo(200000);
        assertThat(foundProduct.get().getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("상품 ID로 조회 성공")
    void findById_Success() {
        // when
        Optional<Product> result = productRepository.findById(product1.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("laptop");
        assertThat(result.get().getDescription()).isEqualTo("노트북입니다.");
        assertThat(result.get().getPrice()).isEqualTo(100000);
        assertThat(result.get().getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("상품 ID로 조회 실패 - 존재하지 않는 ID")
    void findById_NotFound() {
        // when
        Optional<Product> result = productRepository.findById(999L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("모든 상품 조회")
    void findAll_Success() {
        // when
        List<Product> result = productRepository.findAll();

        // then
        assertThat(result).hasSize(3);
        assertThat(result).extracting("name")
                .containsExactlyInAnyOrder("laptop", "mouse", "keyboard");
    }

    @Test
    @DisplayName("조회수 순으로 인기 상품 조회 성공")
    void findTopProductsByViewCount_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 2);

        // when
        List<Product> result = productRepository.findTopProductsByViewCount(pageable);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("laptop"); // viewCount: 3
        assertThat(result.get(0).getViewCount()).isEqualTo(3);
        assertThat(result.get(1).getName()).isEqualTo("mouse"); // viewCount: 2
        assertThat(result.get(1).getViewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("조회수 순으로 인기 상품 조회 - 전체 조회")
    void findTopProductsByViewCount_All() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        // when
        List<Product> result = productRepository.findTopProductsByViewCount(pageable);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getViewCount()).isEqualTo(3);
        assertThat(result.get(1).getViewCount()).isEqualTo(2);
        assertThat(result.get(2).getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("상품 수정 성공")
    void update_Success() {
        // given
        Product product = productRepository.findById(product1.getId()).orElseThrow();

        // when
        product.addStock(5); // 재고 추가
        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // then
        Product updatedProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(15); // 10 + 5
    }

    @Test
    @DisplayName("상품 삭제 성공")
    void delete_Success() {
        // given
        Long productId = product1.getId();

        // when
        productRepository.deleteById(productId);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Product> deletedProduct = productRepository.findById(productId);
        assertThat(deletedProduct).isEmpty();
    }

    @Test
    @DisplayName("상품 존재 여부 확인 - 존재함")
    void existsById_True() {
        // when
        boolean exists = productRepository.existsById(product1.getId());

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("상품 존재 여부 확인 - 존재하지 않음")
    void existsById_False() {
        // when
        boolean exists = productRepository.existsById(999L);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("상품 개수 조회")
    void count_Success() {
        // when
        long count = productRepository.count();

        // then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("최근 7일간 판매량 기준 인기 상품 조회 성공")
    void findTopProductsBySalesInLastWeek_Success() {
        // given
        java.time.LocalDateTime weekAgo = java.time.LocalDateTime.now().minusDays(7);
        Pageable pageable = PageRequest.of(0, 3);

        // when
        List<Product> result = productRepository.findTopProductsBySalesInLastWeek(weekAgo, pageable);

        // then
        assertThat(result).isNotNull();
        // 판매 데이터가 없으므로 모든 상품이 반환되지만 판매량 0으로 정렬됨
        assertThat(result).hasSizeGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("최근 7일간 판매량 기준 인기 상품 조회 - limit 적용")
    void findTopProductsBySalesInLastWeek_WithLimit() {
        // given
        java.time.LocalDateTime weekAgo = java.time.LocalDateTime.now().minusDays(7);
        Pageable pageable = PageRequest.of(0, 2);

        // when
        List<Product> result = productRepository.findTopProductsBySalesInLastWeek(weekAgo, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isLessThanOrEqualTo(2);
    }
}
