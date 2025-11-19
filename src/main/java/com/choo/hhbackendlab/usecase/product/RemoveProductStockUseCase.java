package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 특정 상품의 재고를 차감한다
 */
@Component
@RequiredArgsConstructor
public class RemoveProductStockUseCase {

    private final ProductRepository productRepository;

    @Transactional
    public void removeProductStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));

        //Dirty Checking 자동 update
        product.removeStock(quantity);
    }
}
