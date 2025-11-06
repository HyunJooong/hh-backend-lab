package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.usecase.order.CancelOrderUseCase;
import com.choo.hhbackendlab.usecase.order.CreateOrderUseCase;
import com.choo.hhbackendlab.usecase.order.GetOrderUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    /**
     * 주문 생성 API
     * @param request 주문 생성 요청 정보
     * @return 생성된 주문 정보
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        Order order = createOrderUseCase.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * 주문 조회 API
     * @param orderNumber 주문번호
     * @return 주문 정보
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderNumber) {
        Order order = getOrderUseCase.getOrder(orderNumber);
        return ResponseEntity.ok(order);
    }

    /**
     * 주문 취소 API
     * @param orderNumber 주문번호
     * @return 성공 메시지
     */
    @PatchMapping("/{orderNumber}/cancel")
    public ResponseEntity<String> cancelOrder(@PathVariable String orderNumber) {
        cancelOrderUseCase.cancelOrder(orderNumber);
        return ResponseEntity.ok("주문이 취소되었습니다. 주문번호: " + orderNumber);
    }
}
