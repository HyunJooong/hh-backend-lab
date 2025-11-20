
package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class GetTopProductsUseCaseTest {

/*    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetTopProductsUseCase getTopProductsUseCase;

    @Test
    @DisplayName("인기 상품 조회 성공 - 조회수 내림차순")
    void getTopProducts_Success() {
        // given
        int limit = 3;
        Category category = new Category();

        Product product1 = new Product("laptop", "노트북", category, 100000, 10);
        Product product2 = new Product("mouse", "마우스", category, 30000, 20);
        Product product3 = new Product("keyboard", "키보드", category, 50000, 15);

        // 조회수 설정
        product1.incrementViewCount();
        product1.incrementViewCount();
        product1.incrementViewCount(); // viewCount: 3

        product2.incrementViewCount();
        product2.incrementViewCount(); // viewCount: 2

        product3.incrementViewCount(); // viewCount: 1

        List<Product> mockProducts = Arrays.asList(product1, product2, product3);

        Pageable pageable = PageRequest.of(0, limit);
        given(productRepository.findTopProductsByViewCount(pageable)).willReturn(mockProducts);

        // when
        List<Product> result = getTopProductsUseCase.getTopProducts(limit);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getViewCount()).isEqualTo(3);
        assertThat(result.get(1).getViewCount()).isEqualTo(2);
        assertThat(result.get(2).getViewCount()).isEqualTo(1);

        verify(productRepository).findTopProductsByViewCount(any(Pageable.class));
    }

    @Test
    @DisplayName("인기 상품 조회 실패 - 잘못된 limit 값 (0 이하)")
    void getTopProducts_InvalidLimit() {
        // given
        int invalidLimit = 0;

        // when & then
        assertThatThrownBy(() -> getTopProductsUseCase.getTopProducts(invalidLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조회할 상품 개수는 1개 이상이어야 합니다");
    }

    @Test
    @DisplayName("인기 상품 조회 성공 - 빈 목록 반환")
    void getTopProducts_EmptyList() {
        // given
        int limit = 10;
        Pageable pageable = PageRequest.of(0, limit);

        given(productRepository.findTopProductsByViewCount(pageable)).willReturn(Arrays.asList());

        // when
        List<Product> result = getTopProductsUseCase.getTopProducts(limit);

        // then
        assertThat(result).isEmpty();
        verify(productRepository).findTopProductsByViewCount(any(Pageable.class));
    }

    @Test
    @DisplayName("인기 상품 조회 성공 - 요청한 개수보다 적은 상품")
    void getTopProducts_LessThanLimit() {
        // given
        int limit = 10;
        Category category = new Category();

        Product product1 = new Product("laptop", "노트북", category, 100000, 10);
        Product product2 = new Product("mouse", "마우스", category, 30000, 20);

        product1.incrementViewCount();
        product1.incrementViewCount();
        product2.incrementViewCount();

        List<Product> mockProducts = Arrays.asList(product1, product2);

        Pageable pageable = PageRequest.of(0, limit);
        given(productRepository.findTopProductsByViewCount(pageable)).willReturn(mockProducts);

        // when
        List<Product> result = getTopProductsUseCase.getTopProducts(limit);

        // then
        assertThat(result).hasSize(2); // limit은 10이지만 실제 상품은 2개만 반환
        verify(productRepository).findTopProductsByViewCount(any(Pageable.class));
    }*/
}

