package com.choo.hhbackendlab.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 쿠폰 템플릿
 * 실제 발급은 UserCoupon 엔티티로 관리
 */
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
    private int couponCnt;  // 발급 가능한 쿠폰 개수 (재고)

    @Column(nullable = false)
    private int couponAmount;  // 할인 금액 (고정 금액)

    @Column(nullable = false)
    private int minOrderAmount;  // 최소 주문 금액 (0이면 제한 없음)

    @Column(nullable = false)
    private LocalDateTime expiredAt;  // 만료 일시

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 생성 일시

    /**
     * 쿠폰 템플릿 생성자
     */
    public Coupon(String name, int couponCnt, int couponAmount, int minOrderAmount, LocalDateTime expiredAt) {
        validateCouponInfo(name, couponCnt, couponAmount, minOrderAmount, expiredAt);

        this.name = name;
        this.couponCnt = couponCnt;
        this.couponAmount = couponAmount;
        this.minOrderAmount = minOrderAmount;
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
     * 쿠폰 발급 가능 여부 확인
     */
    public boolean canIssue() {
        if (isExpired()) {
            return false;
        }
        if (couponCnt <= 0) {
            return false;
        }
        return true;
    }

    /**
     * 쿠폰 발급 (couponCnt 감소)
     * 실제 발급 내역은 UserCoupon 엔티티로 관리
     */
    public UserCoupon issueCoupon(User user) {
        if (!canIssue()) {
            throw new IllegalStateException("발급 가능한 쿠폰이 없습니다. 쿠폰명: " + this.name);
        }
        if (user == null) {
            throw new IllegalArgumentException("사용자를 확인할 수 없습니다.");
        }

        // 쿠폰 재고 감소
        this.couponCnt--;

        // UserCoupon 생성 및 반환
        return new UserCoupon(this, user);
    }

    /**
     * 쿠폰 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }

    /**
     * 남은 발급 가능 개수 조회
     */
    public int getRemainingCount() {
        return couponCnt;
    }
}