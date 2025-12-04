package com.choo.hhbackendlab.scheduler;

import com.choo.hhbackendlab.redis.CouponIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis Sorted Set 기반 쿠폰 발급 처리 스케줄러
 * 주기적으로 Redis Sorted Set에서 요청을 꺼내서 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class CouponIssueProcessor {

    private final CouponIssue couponIssue;

    /**
     * Redis Sorted Set 처리 스케줄러
     * 50ms마다 대기열을 확인하여 처리
     * fixedDelay: 이전 작업이 완료된 후 50ms 후에 다시 실행
     *
     * Redis Sorted Set은 DB보다 빠르므로 처리 주기를 짧게 설정
     */
    @Scheduled(fixedDelay = 50)
    public void processWaitingList() {
        try {
            // Redis Sorted Set에서 요청을 하나씩 처리
            // 처리할 요청이 없으면 false 반환
            boolean processed = couponIssue.processNextRequest();

            // 처리할 요청이 있었다면 즉시 다음 요청 처리 시도
            // 처리할 요청이 없으면 50ms 대기 후 다시 시도
            if (processed) {
                // 연속적으로 처리하기 위해 로그만 남김
                log.debug("대기열 처리 완료, 다음 요청 확인");
            }
        } catch (Exception e) {
            log.error("대기열 처리 중 예외 발생", e);
        }
    }

    /**
     * 대기 중인 발급 요청 개수 로그 출력 (1분마다)
     */
    @Scheduled(fixedRate = 60000)
    public void logWaitingListStatus() {
        long pendingCount = couponIssue.getPendingRequestCount();
        if (pendingCount > 0) {
            log.info("대기 중인 쿠폰 발급 요청: {}건", pendingCount);
        }
    }
}