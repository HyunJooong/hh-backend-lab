package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.entity.PointWallet;
import com.choo.hhbackendlab.repository.OrderRepository;
import com.choo.hhbackendlab.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일괄 주문을 취소하고, 포인트를 환불하며, 재고를 복구하고, 쿠폰 사용을 취소한다
 * 일괄 주문취소만 가능
 * 개별 상품 취소는 추후에 개발 예정..
 */
@Component
@RequiredArgsConstructor
public class CancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final PointWalletRepository pointWalletRepository;

    @Transactional
    public void cancelOrder(String orderNumber) {
        // 1. 주문 조회
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. 주문번호: " + orderNumber));

        // 2. PointWallet 조회
        PointWallet pointWallet = pointWalletRepository.findByUserIdWithLock(order.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "포인트 지갑을 찾을 수 없습니다. User ID: " + order.getUser().getId()
                ));

        // 3. 주문 취소 (도메인 메서드로 위임 - 검증, 취소 처리, 재고 복구, 쿠폰 취소 포함)
        order.cancel();

        // 4. 포인트 환불 처리
        pointWallet.refund(order.getRefundAmount());
    }
}
