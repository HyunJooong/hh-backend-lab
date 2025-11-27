package com.choo.hhbackendlab.helper;

import com.choo.hhbackendlab.dto.requestDto.OrderItemRequest;
import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.repository.*;
import com.choo.hhbackendlab.usecase.product.GetTopProductsBySalesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 주문 트랜잭션 처리 컴포넌트
 * Self-invocation 문제를 해결하기 위해 트랜잭션 로직을 분리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTransactionProcessor {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PointWalletRepository pointWalletRepository;
    private final UserCouponRepository userCouponRepository;
    private final GetTopProductsBySalesUseCase topProductsBySalesUseCase;

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

        Order savedOrder = orderRepository.save(order);

        // 트랜잭션 커밋 후 판매량 캐시 무효화 (비동기)
        registerCacheInvalidationAfterCommit();

        return savedOrder;
    }

    /**
     * 트랜잭션 커밋 후 판매량 캐시 무효화
     * 트랜잭션이 완전히 커밋된 후에 캐시를 무효화하여 데이터 정합성 보장
     */
    private void registerCacheInvalidationAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            log.debug("주문 완료 후 판매량 캐시 무효화 시작");
                            topProductsBySalesUseCase.forceRefreshCache();
                        } catch (Exception e) {
                            // 캐시 갱신 실패는 주문 처리에 영향을 주지 않음
                            log.error("판매량 캐시 갱신 실패 (주문은 정상 처리됨)", e);
                        }
                    }
                }
        );
    }
}