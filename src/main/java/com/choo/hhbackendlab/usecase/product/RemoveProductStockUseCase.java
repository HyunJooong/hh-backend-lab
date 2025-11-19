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
        // DB에서 원자적으로 재고 감소
        int updated = productRepository.decreaseStock(productId, quantity);

        // 재고 부족 또는 상품 없음
        if (updated == 0) {
            // 상품이 없는지 재고가 부족한지 확인
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "상품을 찾을 수 없습니다. ID: " + productId));

            throw new IllegalStateException(
                    "재고가 부족합니다. 상품: " + product.getName() +
                            ", 요청 수량: " + quantity +
                            ", 현재 재고: " + product.getStock());
        }
    }
}
