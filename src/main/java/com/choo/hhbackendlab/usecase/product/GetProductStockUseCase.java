package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 특정 상품의 재고 수량을 조회한다
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetProductStockUseCase {

    private final ProductRepository productRepository;

    public int getStock(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));
        return product.getStock();
    }
}
