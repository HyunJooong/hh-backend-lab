package com.choo.hhbackendlab.helper;

import com.choo.hhbackendlab.dto.requestDto.OrderItemRequest;
import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 트랜잭션 처리 컴포넌트
 * Self-invocation 문제를 해결하기 위해 트랜잭션 로직을 분리
 */
@Component
@RequiredArgsConstructor
public class OrderTransactionProcessor {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PointWalletRepository pointWalletRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public Order processOrder(OrderRequest request, User user) {

        Order order = Order.createOrder(user);

        // 재고 차감
        for (OrderItemRequest item : request.getOrderItems()) {
            int updated = productRepository.decreaseStock(
                    item.getProductId(),
                    item.getQuantity()
            );

            if (updated == 0) {
                throw new IllegalStateException(
                        "재고가 부족합니다. 상품 ID: " + item.getProductId());
            }

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "상품을 찾을 수 없습니다. ID: " + item.getProductId()));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(item.getQuantity())
                    .price(product.getPrice())
                    .build();
            order.addOrderItem(orderItem);
        }

        // 쿠폰 적용
        if (request.getCouponId() != null) {
            UserCoupon coupon = userCouponRepository.findById(request.getCouponId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "쿠폰을 찾을 수 없습니다. ID: " + request.getCouponId()));
            order.applyDiscount(coupon);
        }

        // 포인트 차감
        int updated = pointWalletRepository.decreaseBalance(
                user.getId(),
                order.getFinalAmount()
        );

        if (updated == 0) {
            throw new IllegalStateException(
                    "포인트가 부족합니다. 필요 금액: " + order.getFinalAmount());
        }

        return orderRepository.save(order);
    }
}