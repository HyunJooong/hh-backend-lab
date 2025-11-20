package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncrementProductViewCountUseCase {

    private final ProductRepository productRepository;

    /**
     * 상품 조회수를 증가시킵니다.
     * 벌크 업데이트를 사용하여 SELECT 없이 바로 UPDATE를 수행합니다.
     * 여러 사용자가 동시에 조회를 하면 Lost Update 동시성 문제가 발생할 수 있음
     * @param productId 상품 ID
     */
    @Transactional
    public void incrementViewCount(Long productId) {
        // 상품 존재 여부 확인
        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }

        // 벌크 업데이트로 조회수 증가
        productRepository.incrementViewCount(productId);
    }
}
