package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.dto.requestDto.OrderItemRequest;
import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.repository.OrderRepository;
import com.choo.hhbackendlab.repository.PointWalletRepository;
import com.choo.hhbackendlab.repository.ProductRepository;
import com.choo.hhbackendlab.repository.UserCouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
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

    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + request.getUserId()));

        // 2. PointWallet 조회 (비관적 락 적용)
        PointWallet pointWallet = pointWalletRepository.findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("포인트 지갑을 찾을 수 없습니다. User ID: " + request.getUserId()));

        // 3. 주문 생성 (주문번호는 도메인 모델에서 자동 생성)
        Order order = Order.createOrder(user);

        // 4. 주문 아이템 추가 및 재고 차감
        for (OrderItemRequest itemRequest : request.getOrderItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + itemRequest.getProductId()));

            // 재고 차감
            product.removeStock(itemRequest.getQuantity());

            // 주문 아이템 생성
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .price(product.getPrice())
                    .build();

            order.addOrderItem(orderItem);
        }

        // 5. UserCoupon 할인 적용 (도메인 메서드로 위임)
        if (request.getCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.findById(request.getCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다. ID: " + request.getCouponId()));
            order.applyDiscount(userCoupon);
        }

        // 6. 포인트 결제 처리 (도메인 메서드로 위임)
        order.processPayment(pointWallet);

        // 7. 주문 저장
        return orderRepository.save(order);
    }
}
