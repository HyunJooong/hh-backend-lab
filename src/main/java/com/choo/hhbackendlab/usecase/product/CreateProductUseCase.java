package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.dto.requestDto.ProductRequest;
import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.CategoryRepository;
import com.choo.hhbackendlab.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새로운 상품을 등록한다
 */
@Component
@RequiredArgsConstructor
public class CreateProductUseCase {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public Product createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다. ID: " + request.getCategoryId()));

        Product product = new Product(
                request.getName(),
                request.getDescription(),
                category,
                request.getPrice(),
                request.getStock()
        );

        return productRepository.save(product);
    }
}
