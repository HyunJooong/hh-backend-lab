package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 *  특정 상품의 재고를 추가한다
 */
@Component
@RequiredArgsConstructor
public class AddProductStockUseCase {

    private final ProductRepository productRepository;

    @Transactional
    public void addProductStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));
        product.addStock(quantity);
    }
}
