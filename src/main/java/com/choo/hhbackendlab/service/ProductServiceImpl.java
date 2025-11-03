package com.choo.hhbackendlab.service;

import com.choo.hhbackendlab.dto.ProductRequest;
import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.CategoryRepository;
import com.choo.hhbackendlab.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService{

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
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

    @Override
    public int getStock(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));
        return product.getStock();
    }

    @Override
    @Transactional
    public void removeStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));
        product.removeStock(quantity);
    }

    @Override
    @Transactional
    public void addStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));
        product.addStock(quantity);
    }
}
