package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 사용자에게 발급된 쿠폰
 * Coupon(템플릿)과 User의 중간 테이블
 */
@Entity(name = "USER_COUPON")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;  // 쿠폰 템플릿

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;  // 쿠폰 소유자

    @Column(unique = true, nullable = false)
    private String couponCode;  // 쿠폰 코드값
    
    @Column(nullable = false)
    private boolean isUsed;  // 사용 여부

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;  // 발급 일시

    @Column
    private LocalDateTime usedAt;  // 사용 일시

    /**
     * UserCoupon 생성자
     */
    public UserCoupon(Coupon coupon, User user) {
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰을 확인할 수 없습니다.");
        }
        if (user == null) {
            throw new IllegalArgumentException("사용자를 확인할 수 없습니다.");
        }

        this.coupon = coupon;
        this.user = user;
        this.couponCode = generateCouponCode();
        this.isUsed = false;
    }

    /**
     * 쿠폰 고유 코드값 생성
     * 사용자 마다 다른 쿠폰 코드값을 발급해 쿠폰 도용 방지
     * @return
     */
    private String generateCouponCode() {
        // 날짜 + 랜덤값
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomValue = UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();

        return timestamp + "-" + randomValue;
    }

    /**
     * 쿠폰 사용 가능 여부 확인
     */
    public boolean canUse(int orderAmount) {
        // 이미 사용된 쿠폰
        if (isUsed) {
            return false;
        }

        // 만료된 쿠폰
        if (coupon.isExpired()) {
            return false;
        }

        // 최소 주문 금액 미달
        if (orderAmount < coupon.getMinOrderAmount()) {
            return false;
        }

        return true;
    }

    /**
     * 쿠폰 사용
     */
    public void use() {
        if (isUsed) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        }
        if (coupon.isExpired()) {
            throw new IllegalStateException("만료된 쿠폰입니다.");
        }

        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 사용 취소 (주문 취소 시)
     */
    public void cancelUsage() {
        if (!isUsed) {
            throw new IllegalStateException("사용되지 않은 쿠폰입니다.");
        }

        this.isUsed = false;
        this.usedAt = null;
    }

    /**
     * 할인 금액 계산
     */
    public int calculateDiscountAmount(int orderAmount) {
        if (!canUse(orderAmount)) {
            return 0;
        }

        // 할인 금액이 주문 금액보다 클 수 없음
        return Math.min(coupon.getCouponAmount(), orderAmount);
    }
}