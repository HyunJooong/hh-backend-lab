package com.choo.hhbackendlab.redis;

/**
 * 쿠폰 발급 Queue 상태
 */
public enum QueueStatus {
    PENDING,      // 대기 중
    PROCESSING,   // 처리 중
    COMPLETED,    // 완료
    FAILED        // 실패
}