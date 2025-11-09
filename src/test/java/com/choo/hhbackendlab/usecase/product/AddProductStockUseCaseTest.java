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
public class AddProductStockUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private AddProductStockUseCase addProductStockUseCase;

    @Test
    @DisplayName("재고 추가 성공 - 상품 재고가 정상적으로 증가한다")
    void addProductStock_Success() throws Exception {
        // given
        Long productId = 1L;
        int initialStock = 10;
        int quantityToAdd = 5;

        Category category = createCategory(1L, "IT", "IT");
        Product product = createProduct(productId, "laptop", "노트북입니다.", category, 100000, initialStock);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        addProductStockUseCase.addProductStock(productId, quantityToAdd);

        // then
        assertThat(product.getStock()).isEqualTo(15);

        verify(productRepository).findById(productId);
    }

    @Test
    @DisplayName("재고 추가 실패 - 존재하지 않는 상품 ID")
    void addProductStock_ProductNotFound() {
        // given
        Long invalidProductId = 999L;
        int quantityToAdd = 5;

        given(productRepository.findById(invalidProductId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> addProductStockUseCase.addProductStock(invalidProductId, quantityToAdd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");

        verify(productRepository).findById(invalidProductId);
    }

    @Test
    @DisplayName("재고 추가 실패 - 0 이하의 수량")
    void addProductStock_InvalidQuantity() throws Exception {
        // given
        Long productId = 1L;
        int invalidQuantity = 0;

        Category category = createCategory(1L, "IT", "IT");
        Product product = createProduct(productId, "laptop", "노트북입니다.", category, 100000, 10);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> addProductStockUseCase.addProductStock(productId, invalidQuantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("적재 수량은 0보다 커야 합니다");

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
