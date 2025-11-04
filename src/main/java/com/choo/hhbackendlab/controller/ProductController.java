package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.dto.ProductRequest;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.usecase.product.AddProductStockUseCase;
import com.choo.hhbackendlab.usecase.product.CreateProductUseCase;
import com.choo.hhbackendlab.usecase.product.GetProductStockUseCase;
import com.choo.hhbackendlab.usecase.product.RemoveProductStockUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final GetProductStockUseCase getProductStockUseCase;
    private final RemoveProductStockUseCase removeProductStockUseCase;
    private final AddProductStockUseCase addProductStockUseCase;

    /**
     * 상품 등록 API
     * @param request 상품 등록 요청 정보
     * @return 등록된 상품 정보
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request) {
        Product product = createProductUseCase.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    /**
     * 재고 조회 API
     * @param productId 상품 ID
     * @return 재고 수량
     */
    @GetMapping("/{productId}/stock")
    public ResponseEntity<Integer> getStock(@PathVariable Long productId) {
        int stock = getProductStockUseCase.getStock(productId);
        return ResponseEntity.ok(stock);
    }

    /**
     * 재고 차감 API
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     * @return 성공 메시지
     */
    @PatchMapping("/{productId}/stock/remove")
    public ResponseEntity<String> removeStock(
            @PathVariable Long productId,
            @RequestParam int quantity) {
        removeProductStockUseCase.removeProductStock(productId, quantity);
        return ResponseEntity.ok("재고가 " + quantity + "개 차감되었습니다.");
    }

    /**
     * 재고 적재 API
     * @param productId 상품 ID
     * @param quantity 적재할 수량
     * @return 성공 메시지
     */
    @PatchMapping("/{productId}/stock/add")
    public ResponseEntity<String> addStock(
            @PathVariable Long productId,
            @RequestParam int quantity) {
        addProductStockUseCase.addProductStock(productId, quantity);
        return ResponseEntity.ok("재고가 " + quantity + "개 적재되었습니다.");
    }
}
