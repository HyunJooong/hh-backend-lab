package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.dto.requestDto.OrderItemRequest;
import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.helper.DistributedLockHelper;
import com.choo.hhbackendlab.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새로운 주문을 생성하고, 포인트 결제를 처리하며, 재고를 차감한다
 */
@Component
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PointWalletRepository pointWalletRepository;
    private final UserCouponRepository userCouponRepository;
    private final DistributedLockHelper lockHelper;

    public Order createOrder(OrderRequest request) {

        // 1. 기본 검증 및 사용자 조회
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "사용자를 찾을 수 없습니다. ID: " + request.getUserId()));

        // 주문 아이템 검증
        if (request.getOrderItems() == null || request.getOrderItems().isEmpty()) {
            throw new IllegalArgumentException("주문 상품이 없습니다.");
        }

        //  2. 사용자별 분산락 적용
        String lockKey = "order:user:" + user.getId();

        return lockHelper.executeWithLock(lockKey, 5, 10, () -> {
            return processOrderWithDbLock(request, user);
        });
    }

    @Transactional
    protected Order processOrderWithDbLock(OrderRequest request, User user) {

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

