package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "ORDERS")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id")
    private UserCoupon userCoupon;  // 사용된 쿠폰 (선택 사항)

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
     * 쿠폰 할인 적용 (비즈니스 로직)
     */
    public void applyDiscount(UserCoupon userCoupon) {
        if (userCoupon == null) {
            return;
        }
        int discountAmount = userCoupon.calculateDiscountAmount(this.totalAmount);
        setDiscountAmount(discountAmount);
        this.userCoupon = userCoupon;
        userCoupon.use();  // 쿠폰 사용 처리
    }

    /**
     * 결제 처리 (비즈니스 로직)
     */
    public void processPayment(PointWallet pointWallet) {
        if (pointWallet == null) {
            throw new IllegalArgumentException("포인트 지갑을 찾을 수 없습니다.");
        }
        pointWallet.pay(this.finalAmount);
    }

    /**
     * 주문 취소 (비즈니스 로직)
     */
    public void cancel() {
        validateCancellable();
        this.cancelledAt = LocalDateTime.now();
        restoreStock();
        cancelCoupon();
    }

    /**
     * 쿠폰 사용 취소
     */
    private void cancelCoupon() {
        if (this.userCoupon != null) {
            this.userCoupon.cancelUsage();
        }
    }

    /**
     * 취소 가능 여부 검증
     */
    private void validateCancellable() {
        if (this.cancelledAt != null) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }
    }

    /**
     * 재고 복구
     */
    private void restoreStock() {
        for (OrderItem orderItem : orderItems) {
            orderItem.getProduct().addStock(orderItem.getQuantity());
        }
    }

    /**
     * 환불 금액 조회
     */
    public int getRefundAmount() {
        return this.finalAmount;
    }

    /**
     * 취소 여부 확인
     */
    public boolean isCancelled() {
        return this.cancelledAt != null;
    }
}
