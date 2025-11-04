package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "ORDER")
@Getter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderNumber;  // 주문번호

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(nullable = false)
    private int totalAmount;  // 총 주문 금액

    @Column(nullable = false)
    private int discountAmount;  // 할인 금액

    @Column(nullable = false)
    private int finalAmount;  // 최종 결제 금액

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;  // 주문 일시

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;  // 취소 일시

    /**
     * 주문 생성자
     */
    public Order(String orderNumber, User user) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.totalAmount = 0;
        this.discountAmount = 0;
        this.finalAmount = 0;
        this.orderedAt = LocalDateTime.now();
    }

    /**
     * 새로운 주문 생성
     * 주문번호는 도메인 규칙에 따라 자동 생성됨
     */
    public static Order createOrder(User user) {
        String orderNumber = generateOrderNumber();
        return new Order(orderNumber, user);
    }

    /**
     * 주문번호 생성
     * 형식: ORD-XXXXXXXX (8자리 랜덤 대문자)
     */
    private static String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 연관관계 편의 메서드 - OrderItem 추가
     */
    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        if (orderItem.getOrder() != this) {
            orderItem.setOrder(this);
        }
        calculateAmounts();
    }

    /**
     * 총 금액 계산
     */
    private void calculateAmounts() {
        this.totalAmount = orderItems.stream()
                .mapToInt(OrderItem::getTotalPrice)
                .sum();
        this.finalAmount = this.totalAmount - this.discountAmount;
    }

    /**
     * 할인 금액 설정
     */
    public void setDiscountAmount(int discountAmount) {
        this.discountAmount = discountAmount;
        this.finalAmount = this.totalAmount - this.discountAmount;
    }

    /**
     * 주문 취소
     */
    public void cancel() {
        this.cancelledAt = LocalDateTime.now();
    }
}
