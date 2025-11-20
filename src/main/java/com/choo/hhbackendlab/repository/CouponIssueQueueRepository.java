package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.CouponIssueQueue;
import com.choo.hhbackendlab.redis.QueueStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponIssueQueueRepository extends JpaRepository<CouponIssueQueue, Long> {

    /**
     * PENDING 상태의 요청을 생성시간 순으로 하나 조회 (비관적 락 적용)
     * Queue에서 다음 처리할 요청을 가져오는 메서드
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM COUPON_ISSUE_QUEUE q WHERE q.status = :status ORDER BY q.createdAt ASC, q.id ASC LIMIT 1")
    Optional<CouponIssueQueue> findFirstByStatusWithLock(@Param("status") QueueStatus status);

    /**
     * 특정 상태의 요청 목록 조회
     */
    List<CouponIssueQueue> findByStatus(QueueStatus status);

    /**
     * 특정 사용자의 Queue 요청 조회
     */
    List<CouponIssueQueue> findByUserId(Long userId);

    /**
     * 특정 시간 이전에 생성된 PROCESSING 상태의 요청 조회 (타임아웃 감지용)
     */
    @Query("SELECT q FROM COUPON_ISSUE_QUEUE q WHERE q.status = 'PROCESSING' AND q.createdAt < :timeout")
    List<CouponIssueQueue> findProcessingTimeoutRequests(@Param("timeout") LocalDateTime timeout);

    /**
     * 사용자별 대기 중인 요청 수 조회
     */
    @Query("SELECT COUNT(q) FROM COUPON_ISSUE_QUEUE q WHERE q.userId = :userId AND q.status = 'PENDING'")
    long countPendingRequestsByUserId(@Param("userId") Long userId);

    /**
     * 대기 중인 요청 수 조회
     */
    long countByStatus(QueueStatus status);
}