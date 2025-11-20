package com.choo.hhbackendlab.entity;

import com.choo.hhbackendlab.redis.QueueStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 Queue 엔티티
 * Database를 Queue로 활용하여 동시성 제어
 */
@Entity(name = "COUPON_ISSUE_QUEUE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;  // 쿠폰을 발급받을 사용자 ID

    @Column(nullable = false, length = 100)
    private String couponName;  // 발급할 쿠폰 이름

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueueStatus status;  // 처리 상태

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 요청 생성 시간

    @Column
    private LocalDateTime processedAt;  // 처리 완료 시간

    @Column(length = 500)
    private String errorMessage;  // 실패 시 에러 메시지

    @Column
    private Long issuedCouponId;  // 발급된 쿠폰 ID (성공 시)

    /**
     * Queue 요청 생성자
     */
    public CouponIssueQueue(Long userId, String couponName) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (couponName == null || couponName.isBlank()) {
            throw new IllegalArgumentException("쿠폰 이름은 필수입니다.");
        }

        this.userId = userId;
        this.couponName = couponName;
        this.status = QueueStatus.PENDING;
    }

    /**
     * 처리 시작
     */
    public void startProcessing() {
        if (this.status != QueueStatus.PENDING) {
            throw new IllegalStateException("대기 중인 요청만 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = QueueStatus.PROCESSING;
    }

    /**
     * 처리 완료
     */
    public void complete(Long issuedCouponId) {
        if (this.status != QueueStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 요청만 완료할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = QueueStatus.COMPLETED;
        this.issuedCouponId = issuedCouponId;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 처리 실패
     */
    public void fail(String errorMessage) {
        if (this.status != QueueStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 요청만 실패 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = QueueStatus.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 재시도를 위한 상태 초기화
     */
    public void retry() {
        if (this.status != QueueStatus.FAILED) {
            throw new IllegalStateException("실패한 요청만 재시도할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = QueueStatus.PENDING;
        this.errorMessage = null;
        this.processedAt = null;
    }
}