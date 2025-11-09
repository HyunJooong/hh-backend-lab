package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문번호로 주문과 주문 상품 목록을 조회한다
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrderUseCase {

    private final OrderRepository orderRepository;

    /**
     * 주문 조회 (주문 아이템 + 상품 정보 포함)
     * 예: 가위 2개, 컴퓨터 3개 등 주문한 상품 목록 전체 조회
     */
    public Order getOrder(String orderNumber) {
        return orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. 주문번호: " + orderNumber));
    }
}
