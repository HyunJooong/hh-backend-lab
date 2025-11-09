package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.dto.requestDto.ProductRequest;
import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.CategoryRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class CreateProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CreateProductUseCase createProductUseCase;

    @Test
    @DisplayName("상품 생성 성공 - laptop 상품이 정상적으로 생성된다")
    void createProduct_Success() throws Exception {
        // given
        Long categoryId = 1L;
        Category category = createCategory(categoryId, "IT", "0000");

        ProductRequest request = createProductRequest(
                "laptop",
                "노트북입니다.",
                100000,
                10,
                categoryId
        );

        Product savedProduct = new Product(
                request.getName(),
                request.getDescription(),
                category,
                request.getPrice(),
                request.getStock()
        );
        setProductId(savedProduct, 1L);

        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));
        given(productRepository.save(any(Product.class))).willReturn(savedProduct);

        // when
        Product result = createProductUseCase.createProduct(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("laptop");
        assertThat(result.getDescription()).isEqualTo("노트북입니다.");
        assertThat(result.getPrice()).isEqualTo(100000);
        assertThat(result.getStock()).isEqualTo(10);
        assertThat(result.getCategory()).isEqualTo(category);
        assertThat(result.getCategory().getName()).isEqualTo("IT");

        verify(categoryRepository).findById(categoryId);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("상품 생성 실패 - 존재하지 않는 카테고리로 상품 생성 시 예외 발생")
    void createProduct_CategoryNotFound() {
        // given
        Long invalidCategoryId = 999L;
        ProductRequest request = createProductRequest(
                "laptop",
                "노트북입니다.",
                100000,
                10,
                invalidCategoryId
        );

        given(categoryRepository.findById(invalidCategoryId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> createProductUseCase.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");

        verify(categoryRepository).findById(invalidCategoryId);
    }


    private ProductRequest createProductRequest(String name, String description, int price, int stock, Long categoryId) {
        try {
            ProductRequest request = new ProductRequest();
            setField(request, "name", name);
            setField(request, "description", description);
            setField(request, "price", price);
            setField(request, "stock", stock);
            setField(request, "categoryId", categoryId);
            return request;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ProductRequest", e);
        }
    }

    private Category createCategory(Long id, String name, String code) throws Exception {
        Category category = new Category();
        setField(category, "id", id);
        setField(category, "name", name);
        setField(category, "code", code);
        return category;
    }

    private void setProductId(Product product, Long id) throws Exception {
        setField(product, "id", id);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
