package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.dto.requestDto.OrderItemRequest;
import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.entity.OrderItem;
import com.choo.hhbackendlab.entity.PointWallet;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.repository.OrderRepository;
import com.choo.hhbackendlab.repository.PointWalletRepository;
import com.choo.hhbackendlab.repository.ProductRepository;
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

        // 5. Coupon 할인 금액 적용 (요청에 할인 금액이 있는 경우)
        if (request.getCouponAmount() > 0) {
            order.setDiscountAmount(request.getCouponAmount());
        }

        // 6. 포인트 결제 처리
        int paymentAmount = order.getFinalAmount();

        // 잔액 확인
        if (!pointWallet.hasEnoughBalance(paymentAmount)) {
            throw new IllegalStateException(
                    "포인트 잔액이 부족합니다. 필요 금액: " + paymentAmount +
                    ", 현재 잔액: " + pointWallet.getBalance()
            );
        }

        // 포인트 차감
        pointWallet.use(paymentAmount);

        // 7. 주문 저장
        return orderRepository.save(order);
    }
}
