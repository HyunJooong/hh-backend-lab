package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetTopProductsUseCase {

    private final ProductRepository productRepository;

    /**
     * 조회수 기준 인기 상품을 조회합니다.
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록 (조회수 내림차순)
     */
    @Transactional(readOnly = true)
    public List<Product> getTopProducts(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("조회할 상품 개수는 1개 이상이어야 합니다.");
        }

        Pageable pageable = PageRequest.of(0, limit);
        return productRepository.findTopProductsByViewCount(pageable);
    }
}
