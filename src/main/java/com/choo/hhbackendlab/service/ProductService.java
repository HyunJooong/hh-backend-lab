package com.choo.hhbackendlab.service;

import com.choo.hhbackendlab.dto.ProductRequest;
import com.choo.hhbackendlab.entity.Product;
import org.springframework.stereotype.Service;

@Service
public interface ProductService {

    /**
     * 상품을 등록합니다.
     * @param request 상품 등록 요청 정보
     * @return 등록된 상품
     */
    Product createProduct(ProductRequest request);

    /**
     * 상품의 재고를 조회합니다.
     * @param productId 상품 ID
     * @return 재고 수량
     */
    int getStock(Long productId);

    /**
     * 상품의 재고를 차감합니다.
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     */
    void removeStock(Long productId, int quantity);

    /**
     * 상품의 재고를 적재합니다.
     * @param productId 상품 ID
     * @param quantity 적재할 수량
     */
    void addStock(Long productId, int quantity);
}
