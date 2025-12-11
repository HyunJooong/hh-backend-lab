package com.choo.hhbackendlab.helper;

import com.choo.hhbackendlab.dto.requestDto.OrderItemRequest;
import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.repository.*;
import com.choo.hhbackendlab.usecase.product.GetTopProductsBySalesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 주문 트랜잭션 처리 컴포넌트 (보상 트랜잭션 포함)
 *
 * 처리 순서:
 * 1. 핵심 트랜잭션: 주문 생성 + 포인트 차감
 * 2. 부가 트랜잭션: 재고 차감 (실패 시 보상)
 * 3. 부가 트랜잭션: 쿠폰 적용 (실패 시 보상)
 * 4. 주문 확정
 * 5. 비동기: Redis 판매량 증가
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

    /**
     * 주문 처리 메인 메서드
     * 각 단계별로 트랜잭션을 분리하고, 실패 시 보상 처리
     */
    public Order processOrder(OrderRequest request, User user) {

        Order order = null;

        try {
            // 1단계: 핵심 트랜잭션 - 주문 생성 + 포인트 차감
            log.debug("1단계: 주문 생성 및 포인트 차감");
            order = createOrderAndDecreasePoint(request, user);

            // 2단계: 부가 트랜잭션 - 재고 차감 및 OrderItem 생성
            log.debug("2단계: 재고 차감");
            decreaseStockAndCreateOrderItems(order, request);

            // 3단계: 부가 트랜잭션 - 쿠폰 적용 (선택적)
            if (request.getCouponId() != null) {
                log.debug("3단계: 쿠폰 적용");
                applyCoupon(order, request.getCouponId());
            }

            // 4단계: 주문 확정
            log.debug("4단계: 주문 확정");
            confirmOrder(order);

            // 5단계: 비동기 처리 등록 (Redis 판매량 증가)
            registerSalesIncrementAfterCommit(request);

            log.info("주문 처리 완료: {}", order.getOrderNumber());
            return order;

        } catch (Exception e) {
            log.error("주문 처리 실패: {}", e.getMessage(), e);

            // 보상 트랜잭션 실행
            if (order != null) {
                compensateOrder(order);
            }

            throw new IllegalStateException("주문 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 1단계: 주문 생성 + 포인트 차감 (핵심 트랜잭션)
     * 가장 중요한 로직만 포함
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Order createOrderAndDecreasePoint(OrderRequest request, User user) {

        // 주문 생성 (PENDING 상태)
        Order order = Order.createOrder(user);
        order.setStatus("PENDING");

        // 임시 금액 계산 (정확한 금액은 OrderItem 생성 후 확정)
        int estimatedAmount = request.getOrderItems().stream()
                .mapToInt(item -> {
                    Product product = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "상품을 찾을 수 없습니다. ID: " + item.getProductId()));
                    return product.getPrice() * item.getQuantity();
                })
                .sum();

        // 포인트 차감 (원자적 연산)
        int updated = pointWalletRepository.decreaseBalance(user.getId(), estimatedAmount);
        if (updated == 0) {
            throw new IllegalStateException(
                    "포인트가 부족합니다. 필요 금액: " + estimatedAmount);
        }

        // 주문 저장
        Order savedOrder = orderRepository.save(order);

        log.debug("주문 생성 완료: {}, 포인트 차감: {}",
                savedOrder.getOrderNumber(), estimatedAmount);

        return savedOrder;
    }

    /**
     * 2단계: 재고 차감 및 OrderItem 생성 (별도 트랜잭션)
     * 실패 시: 주문 취소 + 포인트 환불 (보상)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void decreaseStockAndCreateOrderItems(Order order, OrderRequest request) {

        for (OrderItemRequest item : request.getOrderItems()) {

            // 재고 차감 (원자적 연산)
            int updated = productRepository.decreaseStock(
                    item.getProductId(),
                    item.getQuantity()
            );

            if (updated == 0) {
                throw new IllegalStateException(
                        "재고가 부족합니다. 상품 ID: " + item.getProductId());
            }

            // Product 조회
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "상품을 찾을 수 없습니다. ID: " + item.getProductId()));

            // OrderItem 생성
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(item.getQuantity())
                    .price(product.getPrice())
                    .build();

            order.addOrderItem(orderItem);
        }

        log.debug("재고 차감 완료: {} 개 상품", request.getOrderItems().size());
    }

    /**
     * 3단계: 쿠폰 적용 (별도 트랜잭션)
     * 실패 시: 주문 취소 + 포인트 환불 + 재고 복구 (보상)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void applyCoupon(Order order, Long couponId) {

        UserCoupon coupon = userCouponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "쿠폰을 찾을 수 없습니다. ID: " + couponId));

        order.applyDiscount(coupon);

        log.debug("쿠폰 적용 완료: {} 할인", order.getDiscountAmount());
    }

    /**
     * 4단계: 주문 확정 (별도 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void confirmOrder(Order order) {
        order.confirm();
        orderRepository.save(order);

        log.debug("주문 확정 완료: {}", order.getOrderNumber());
    }

    /**
     * 보상 트랜잭션: 주문 취소 + 포인트 환불
     * 재고는 Order.cancel() 내부에서 자동 복구
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void compensateOrder(Order order) {

        log.warn("보상 트랜잭션 시작: 주문번호 {}", order.getOrderNumber());

        try {
            // 1. 주문 취소 (재고 복구 포함)
            order.cancel();

            // 2. 포인트 환불
            PointWallet pointWallet = pointWalletRepository
                    .findByUserId(order.getUser().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "포인트 지갑을 찾을 수 없습니다."));

            pointWallet.refund(order.getFinalAmount());

            log.warn("보상 트랜잭션 완료: 주문 취소 및 포인트 환불");

        } catch (Exception e) {
            log.error("보상 트랜잭션 실패: {}", e.getMessage(), e);
            // 보상 실패는 별도 처리 (알림, 수동 개입 등)
            throw new IllegalStateException("보상 트랜잭션 실패", e);
        }
    }

    /**
     * 트랜잭션 커밋 후 Redis 판매량 실시간 증가
     * 트랜잭션이 완전히 커밋된 후에 Redis Sorted Set 스코어를 증가시켜 데이터 정합성 보장
     */
    private void registerSalesIncrementAfterCommit(OrderRequest request) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            log.debug("주문 완료 후 판매량 실시간 증가 시작");

                            // 주문한 각 상품의 판매량을 Redis에 반영
                            for (OrderItemRequest item : request.getOrderItems()) {
                                topProductsBySalesUseCase.incrementSales(
                                        item.getProductId(),
                                        item.getQuantity()
                                );
                            }

                            log.debug("판매량 증가 완료: {} 개 상품", request.getOrderItems().size());
                        } catch (Exception e) {
                            // Redis 오류는 주문 처리에 영향을 주지 않음
                            log.error("판매량 증가 실패 (주문은 정상 처리됨)", e);
                        }
                    }
                }
        );
    }
}