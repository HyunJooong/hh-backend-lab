package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.dto.OrderItemRequest;
import com.choo.hhbackendlab.dto.OrderRequest;
import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.entity.OrderItem;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.repository.OrderRepository;
import com.choo.hhbackendlab.repository.ProductRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새로운 주문을 생성하고, 재고를 차감한다
 */
@Component
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + request.getUserId()));

        // 2. 주문 생성 (주문번호는 도메인 모델에서 자동 생성)
        Order order = Order.createOrder(user);

        // 3. 주문 아이템 추가
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

        // 4. 주문 저장
        return orderRepository.save(order);
    }
}
