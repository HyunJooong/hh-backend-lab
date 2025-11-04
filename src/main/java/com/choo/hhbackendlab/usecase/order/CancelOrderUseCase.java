package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.entity.OrderItem;
import com.choo.hhbackendlab.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일괄 주문을 취소하고 재고를 복구한다
 * 일괄 주문취소만 가능
 * 개별 상품 취소는 추후에 개발 예정..
 */
@Component
@RequiredArgsConstructor
public class CancelOrderUseCase {

    private final OrderRepository orderRepository;

    @Transactional
    public void cancelOrder(String orderNumber) {
        // 1. 주문 조회
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. 주문번호: " + orderNumber));

        // 2. 이미 취소된 주문인지 확인
        if (order.getCancelledAt() != null) {
            throw new IllegalStateException("이미 취소된 주문입니다. 주문번호: " + orderNumber);
        }

        // 3. 재고 복구
        for (OrderItem orderItem : order.getOrderItems()) {
            orderItem.getProduct().addStock(orderItem.getQuantity());
        }

        // 4. 주문 취소
        order.cancel();
    }
}
