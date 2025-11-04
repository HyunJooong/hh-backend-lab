package com.choo.hhbackendlab.service;

import com.choo.hhbackendlab.dto.ProductRequest;
import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.CategoryRepository;
import com.choo.hhbackendlab.repository.ProductRepository;
import com.choo.hhbackendlab.usecase.product.CreateProductUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateProductUseCase 테스트")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CreateProductUseCase createProductUseCase;

    private Category category;
    private ProductRequest productRequest;

    @BeforeEach
    void setUp() {
        // 테스트용 카테고리 생성
        category = new Category();

        // 테스트용 상품 요청 데이터 생성 (리플렉션을 사용하여 필드 설정)
        productRequest = createProductRequest("노트북", "고성능 노트북", 1500000, 10, 1L);
    }

    @Test
    @DisplayName("상품 등록 성공 테스트")
    void createProduct_Success() {
        // given
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        Product savedProduct = new Product("노트북", "고성능 노트북", category, 1500000, 10);
        given(productRepository.save(any(Product.class))).willReturn(savedProduct);

        // when
        Product result = createProductUseCase.createProduct(productRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("노트북");
        assertThat(result.getDescription()).isEqualTo("고성능 노트북");
        assertThat(result.getPrice()).isEqualTo(1500000);
        assertThat(result.getStock()).isEqualTo(10);
        assertThat(result.getCategory()).isEqualTo(category);

        verify(categoryRepository).findById(1L);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("카테고리가 존재하지 않으면 예외 발생")
    void createProduct_CategoryNotFound() {
        // given
        given(categoryRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> createProductUseCase.createProduct(productRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("카테고리를 찾을 수 없습니다. ID: 1");

        verify(categoryRepository).findById(1L);
    }

    @Test
    @DisplayName("다양한 상품 데이터로 등록 테스트")
    void createProduct_WithVariousData() {
        // given
        ProductRequest request = createProductRequest("마우스", "무선 마우스", 50000, 100, 1L);
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        Product savedProduct = new Product("마우스", "무선 마우스", category, 50000, 100);
        given(productRepository.save(any(Product.class))).willReturn(savedProduct);

        // when
        Product result = createProductUseCase.createProduct(request);

        // then
        assertThat(result.getName()).isEqualTo("마우스");
        assertThat(result.getDescription()).isEqualTo("무선 마우스");
        assertThat(result.getPrice()).isEqualTo(50000);
        assertThat(result.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("재고가 0인 상품 등록 테스트")
    void createProduct_WithZeroStock() {
        // given
        ProductRequest request = createProductRequest("품절 상품", "재고 없음", 10000, 0, 1L);
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        Product savedProduct = new Product("품절 상품", "재고 없음", category, 10000, 0);
        given(productRepository.save(any(Product.class))).willReturn(savedProduct);

        // when
        Product result = createProductUseCase.createProduct(request);

        // then
        assertThat(result.getStock()).isEqualTo(0);
    }

    // ProductRequest 객체를 생성하는 헬퍼 메서드 (리플렉션 사용)
    private ProductRequest createProductRequest(String name, String description, int price, int stock, Long categoryId) {
        try {
            ProductRequest request = new ProductRequest();

            java.lang.reflect.Field nameField = ProductRequest.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(request, name);

            java.lang.reflect.Field descField = ProductRequest.class.getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(request, description);

            java.lang.reflect.Field priceField = ProductRequest.class.getDeclaredField("price");
            priceField.setAccessible(true);
            priceField.set(request, price);

            java.lang.reflect.Field stockField = ProductRequest.class.getDeclaredField("stock");
            stockField.setAccessible(true);
            stockField.set(request, stock);

            java.lang.reflect.Field categoryIdField = ProductRequest.class.getDeclaredField("categoryId");
            categoryIdField.setAccessible(true);
            categoryIdField.set(request, categoryId);

            return request;
        } catch (Exception e) {
            throw new RuntimeException("ProductRequest 생성 실패", e);
        }
    }
}