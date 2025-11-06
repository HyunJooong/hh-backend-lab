package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity(name = "COUPON")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;  // 쿠폰 이름

    @Column(nullable = false)
    private int couponCnt; //쿠폰 갯수

    @Column(nullable = false)
    private int couponAmount;  // 할인 금액 (고정 금액)

    @Column(nullable = false)
    private int minOrderAmount;  // 최소 주문 금액 (0이면 제한 없음)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;  // 쿠폰 소유자 (null이면 미발급 쿠폰)

    @Column(nullable = false)
    private boolean isUsed;  // 사용 여부

    @Column
    private LocalDateTime issuedAt;  // 발급 일시

    @Column
    private LocalDateTime usedAt;  // 사용 일시

    @Column(nullable = false)
    private LocalDateTime expiredAt;  // 만료 일시

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 생성 일시

    /**
     * 쿠폰 생성자 (미발급 쿠폰)
     */
    public Coupon(String name, int couponCnt, int couponAmount, int minOrderAmount, LocalDateTime expiredAt) {
        validateCouponInfo(name, couponCnt, couponAmount, minOrderAmount, expiredAt);

        this.name = name;
        this.couponCnt = couponCnt;
        this.couponAmount = couponAmount;
        this.minOrderAmount = minOrderAmount;
        this.isUsed = false;
        this.expiredAt = expiredAt;
    }

    /**
     * 쿠폰 정보 유효성 검증
     */
    private void validateCouponInfo(String name, int couponCnt, int couponAmount, int minOrderAmount, LocalDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("쿠폰 이름을 입력해 주세요.");
        }
        if (couponCnt <= 0) {
            throw new IllegalArgumentException("쿠폰 발행 수량은 0보다 커야 합니다.");
        }
        if (couponAmount <= 0) {
            throw new IllegalArgumentException("쿠폰 금액은 0보다 커야 합니다.");
        }
        if (minOrderAmount < 0) {
            throw new IllegalArgumentException("최소 주문 금액은 0보다 작을 수 없습니다.");
        }
        if (expiredAt == null || expiredAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("유효한 만료 일시를 입력해 주세요.");
        }
    }

    /**
     * 쿠폰 발급
     */
    public void issue(User user) {
        if (this.user != null) {
            throw new IllegalStateException("이미 발급된 쿠폰입니다.");
        }
        if (user == null) {
            throw new IllegalArgumentException("사용자를 확인할 수 없습니다.");
        }
        if (isExpired()) {
            throw new IllegalStateException("만료된 쿠폰은 발급할 수 없습니다.");
        }

        this.user = user;
        this.issuedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 사용 가능 여부 확인
     */
    public boolean canUse(int orderAmount) {
        // 사용자 확인 불가
        if (user == null) {
            return false;
        }

        // 이미 사용된 쿠폰
        if (isUsed) {
            return false;
        }

        // 만료된 쿠폰
        if (isExpired()) {
            return false;
        }

        // 최소 주문 금액 미달
        if (orderAmount < minOrderAmount) {
            return false;
        }

        return true;
    }

    /**
     * 쿠폰 사용
     */
    public void use() {
        if (user == null) {
            throw new IllegalStateException("발급되지 않은 쿠폰입니다.");
        }
        if (isUsed) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        }
        if (isExpired()) {
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
        return Math.min(couponAmount, orderAmount);
    }

    /**
     * 쿠폰 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }

    /**
     * 쿠폰 발급 여부 확인
     */
    public boolean isIssued() {
        return user != null;
    }
}