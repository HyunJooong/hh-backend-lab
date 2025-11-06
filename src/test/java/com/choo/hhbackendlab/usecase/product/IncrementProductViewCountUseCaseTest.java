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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class IncrementProductViewCountUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private IncrementProductViewCountUseCase incrementProductViewCountUseCase;

    @Test
    @DisplayName("조회수 증가 성공")
    void incrementViewCount_Success() {
        // given
        Long productId = 1L;
        Category category = new Category();
        Product product = new Product("laptop", "노트북입니다.", category, 100000, 10);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        long initialViewCount = product.getViewCount();

        // when
        incrementProductViewCountUseCase.incrementViewCount(productId);

        // then
        assertThat(product.getViewCount()).isEqualTo(initialViewCount + 1);
        verify(productRepository).findById(productId);
    }

    @Test
    @DisplayName("조회수 증가 실패 - 존재하지 않는 상품")
    void incrementViewCount_ProductNotFound() {
        // given
        Long invalidProductId = 999L;

        given(productRepository.findById(invalidProductId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> incrementProductViewCountUseCase.incrementViewCount(invalidProductId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");

        verify(productRepository).findById(invalidProductId);
    }

    @Test
    @DisplayName("조회수 여러 번 증가")
    void incrementViewCount_Multiple() {
        // given
        Long productId = 1L;
        Category category = new Category();
        Product product = new Product("laptop", "노트북입니다.", category, 100000, 10);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        long initialViewCount = product.getViewCount();

        // when
        incrementProductViewCountUseCase.incrementViewCount(productId);
        incrementProductViewCountUseCase.incrementViewCount(productId);
        incrementProductViewCountUseCase.incrementViewCount(productId);

        // then
        assertThat(product.getViewCount()).isEqualTo(initialViewCount + 3);
    }
}
