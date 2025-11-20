package com.choo.hhbackendlab.service;

import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.redis.QueueStatus;
import com.choo.hhbackendlab.repository.CouponIssueQueueRepository;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserCouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Queue 기반 쿠폰 발급 서비스
 * Database를 Queue로 활용하여 동시성 제어
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssue {

    private final CouponIssueQueueRepository queueRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * Queue에 쿠폰 발급 요청 추가
     *
     * @param userId 사용자 ID
     * @param couponName 쿠폰 이름
     * @return Queue ID
     */
    @Transactional
    public Long addToQueue(Long userId, String couponName) {
        log.info("Queue에 쿠폰 발급 요청 추가 - userId: {}, couponName: {}", userId, couponName);

        CouponIssueQueue queueItem = new CouponIssueQueue(userId, couponName);
        CouponIssueQueue saved = queueRepository.save(queueItem);

        log.info("Queue 요청 추가 완료 - queueId: {}", saved.getId());
        return saved.getId();
    }

    /**
     * Queue에서 하나의 요청을 꺼내서 처리
     * 비관적 락을 사용하여 동시성 제어
     *
     * @return 처리 완료 여부
     */
    @Transactional
    public boolean processNextQueue() {
        // 1. PENDING 상태의 요청을 하나 가져오기 (비관적 락 적용)
        Optional<CouponIssueQueue> queueItemOpt = queueRepository
                .findFirstByStatusWithLock(QueueStatus.PENDING);

        if (queueItemOpt.isEmpty()) {
            log.debug("처리할 Queue 요청이 없습니다.");
            return false;
        }

        CouponIssueQueue queueItem = queueItemOpt.get();
        log.info("Queue 요청 처리 시작 - queueId: {}, userId: {}, couponName: {}",
                queueItem.getId(), queueItem.getUserId(), queueItem.getCouponName());

        try {
            // 2. 처리 시작 상태로 변경
            queueItem.startProcessing();

            // 3. 실제 쿠폰 발급 처리
            Long issuedCouponId = issueCoupon(queueItem.getUserId(), queueItem.getCouponName());

            // 4. 처리 완료
            queueItem.complete(issuedCouponId);
            log.info("Queue 요청 처리 완료 - queueId: {}, issuedCouponId: {}",
                    queueItem.getId(), issuedCouponId);

            return true;

        } catch (Exception e) {
            // 5. 처리 실패
            queueItem.fail(e.getMessage());
            log.error("Queue 요청 처리 실패 - queueId: {}, error: {}",
                    queueItem.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 실제 쿠폰 발급 로직
     *
     * @param userId 사용자 ID
     * @param couponName 쿠폰 이름
     * @return 발급된 UserCoupon ID
     */
    private Long issueCoupon(Long userId, String couponName) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        // 발급 가능한 쿠폰 템플릿 조회 (비관적 락)
        Coupon coupon = couponRepository.findFirstAvailableCouponByNameWithLock(couponName)
                .orElseThrow(() -> new IllegalStateException("발급 가능한 쿠폰이 없습니다. 쿠폰명: " + couponName));

        // 쿠폰 발급 (couponCnt 감소 및 UserCoupon 생성)
        UserCoupon userCoupon = coupon.issueCoupon(user);

        // UserCoupon 저장
        UserCoupon saved = userCouponRepository.save(userCoupon);

        return saved.getId();
    }

    /**
     * 대기 중인 Queue 개수 조회
     */
    @Transactional(readOnly = true)
    public long getPendingQueueCount() {
        return queueRepository.countByStatus(QueueStatus.PENDING);
    }

    /**
     * 사용자별 대기 중인 Queue 개수 조회
     */
    @Transactional(readOnly = true)
    public long getPendingQueueCountByUserId(Long userId) {
        return queueRepository.countPendingRequestsByUserId(userId);
    }

    /**
     * Queue 상태 조회
     */
    @Transactional(readOnly = true)
    public CouponIssueQueue getQueueStatus(Long queueId) {
        return queueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue 요청을 찾을 수 없습니다. Queue ID: " + queueId));
    }
}