package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.dto.requestDto.ProductRequest;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.usecase.product.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final GetProductStockUseCase getProductStockUseCase;
    private final RemoveProductStockUseCase removeProductStockUseCase;
    private final AddProductStockUseCase addProductStockUseCase;
    private final IncrementProductViewCountUseCase incrementProductViewCountUseCase;
    private final GetTopProductsUseCase getTopProductsUseCase;
    private final GetTopProductsBySalesUseCase getTopProductsBySalesUseCase;

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

    /**
     * 상품 조회수 증가 API
     * @param productId 상품 ID
     * @return 성공 메시지
     */
    @PatchMapping("/{productId}/view")
    public ResponseEntity<String> incrementViewCount(@PathVariable Long productId) {
        incrementProductViewCountUseCase.incrementViewCount(productId);
        return ResponseEntity.ok("조회수가 증가되었습니다.");
    }

    /**
     * 인기 상품 조회 API (조회수 기준)
     * @param limit 조회할 상품 개수 (기본값: 10)
     * @return 인기 상품 목록
     */
    @GetMapping("/top")
    public ResponseEntity<List<Product>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit) {
        List<Product> topProducts = getTopProductsUseCase.getTopProducts(limit);
        return ResponseEntity.ok(topProducts);
    }

    /**
     * 인기 상품 조회 API (최근 7일 판매량 기준)
     * @param limit 조회할 상품 개수 (기본값: 10)
     * @return 인기 상품 목록
     */
    @GetMapping("/top/sales")
    public ResponseEntity<List<Product>> getTopProductsBySales(
            @RequestParam(defaultValue = "10") int limit) {
        List<Product> topProducts = getTopProductsBySalesUseCase.getTopProductsBySales(limit);
        return ResponseEntity.ok(topProducts);
    }
}
