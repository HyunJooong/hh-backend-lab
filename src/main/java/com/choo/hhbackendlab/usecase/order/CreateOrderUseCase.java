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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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



        // 2. 주문번호 생성
        Order order = Order.createOrder(user);

        /*// 3. 주문 아이템 추가 및 재고 차감
        // 비관적락을 사용해서 상품 조회
        for (OrderItemRequest itemRequest : request.getOrderItems()) {
            Product product = productRepository.findByIdWithLock(itemRequest.getProductId())
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
        }*/

        // 3. 상품 ID 리스트 추출 및 정렬 (락 순서 일관성 보장)
        List<Long> productIds = request.getOrderItems().stream()
                .map(OrderItemRequest::getProductId)
                .sorted()  // ⭐ ID 순서로 정렬하여 데드락 방지
                .toList();

        // 4. 재고 차감 (DB 쿼리로 원자적 처리 - 락 불필요!)
        for (OrderItemRequest itemRequest : request.getOrderItems()) {
            int updated = productRepository.decreaseStock(
                    itemRequest.getProductId(),
                    itemRequest.getQuantity()
            );

            if (updated == 0) {
                throw new IllegalStateException(
                        "재고가 부족합니다. 상품 ID: " + itemRequest.getProductId());
            }
        }

        // 5. 상품 조회 (재고는 이미 감소됨, 락 불필요)
        List<Product> products = productRepository.findAllById(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 6. 주문 아이템 추가 및 재고 차감
        for (OrderItemRequest itemRequest : request.getOrderItems()) {
            Product product = productMap.get(itemRequest.getProductId());

            // 주문 아이템 생성
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .price(product.getPrice())
                    .build();

            order.addOrderItem(orderItem);
        }

        // 7. UserCoupon 할인 적용
        if (request.getCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.findById(request.getCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다. ID: " + request.getCouponId()));
            order.applyDiscount(userCoupon);
        }

        // 8. 포인트 결제 (DB 쿼리로 처리)
        int updated = pointWalletRepository.decreaseBalance(
                request.getUserId(),
                order.getFinalAmount()
        );

        if (updated == 0) {
            throw new IllegalStateException("포인트가 부족합니다.");
        }

        // 7. 주문 저장
        return orderRepository.save(order);
    }
}
