package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.entity.OrderItem;
import com.choo.hhbackendlab.entity.PointWallet;
import com.choo.hhbackendlab.repository.OrderRepository;
import com.choo.hhbackendlab.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일괄 주문을 취소하고, 포인트를 환불하며, 재고를 복구한다
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

        // 2. 이미 취소된 주문인지 확인
        if (order.getCancelledAt() != null) {
            throw new IllegalStateException("이미 취소된 주문입니다. 주문번호: " + orderNumber);
        }

        // 3. 포인트 환불 처리
        PointWallet pointWallet = pointWalletRepository.findByUser(order.getUser())
                .orElseThrow(() -> new IllegalArgumentException(
                        "포인트 지갑을 찾을 수 없습니다. User ID: " + order.getUser().getId()
                ));

        int refundAmount = order.getFinalAmount();
        pointWallet.refund(refundAmount);

        // 4. 재고 복구
        for (OrderItem orderItem : order.getOrderItems()) {
            orderItem.getProduct().addStock(orderItem.getQuantity());
        }

        // 5. 주문 취소
        order.cancel();
    }
}
