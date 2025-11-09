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

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class GetProductStockUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetProductStockUseCase getProductStockUseCase;

    @Test
    @DisplayName("재고 조회 성공 - 상품의 현재 재고를 반환한다")
    void getStock_Success() throws Exception {
        // given
        Long productId = 1L;
        int expectedStock = 15;

        Category category = createCategory(1L, "IT", "IT");
        Product product = createProduct(productId, "laptop", "노트북입니다.", category, 100000, expectedStock);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        int actualStock = getProductStockUseCase.getStock(productId);

        // then
        assertThat(actualStock).isEqualTo(expectedStock);

        verify(productRepository).findById(productId);
    }

    @Test
    @DisplayName("재고 조회 실패 - 존재하지 않는 상품 ID")
    void getStock_ProductNotFound() {
        // given
        Long invalidProductId = 999L;

        given(productRepository.findById(invalidProductId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> getProductStockUseCase.getStock(invalidProductId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");

        verify(productRepository).findById(invalidProductId);
    }

    @Test
    @DisplayName("재고 조회 성공 - 재고가 0인 상품")
    void getStock_ZeroStock() throws Exception {
        // given
        Long productId = 1L;
        int zeroStock = 0;

        Category category = createCategory(1L, "IT", "IT");
        Product product = createProduct(productId, "laptop", "노트북입니다.", category, 100000, zeroStock);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        int actualStock = getProductStockUseCase.getStock(productId);

        // then
        assertThat(actualStock).isEqualTo(0);

        verify(productRepository).findById(productId);
    }

    // Helper methods
    private Category createCategory(Long id, String name, String code) throws Exception {
        Category category = new Category();
        setField(category, "id", id);
        setField(category, "name", name);
        setField(category, "code", code);
        return category;
    }

    private Product createProduct(Long id, String name, String description, Category category, int price, int stock) throws Exception {
        Product product = new Product(name, description, category, price, stock);
        setField(product, "id", id);
        return product;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
