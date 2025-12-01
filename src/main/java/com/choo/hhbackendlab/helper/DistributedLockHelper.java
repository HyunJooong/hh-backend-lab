package com.choo.hhbackendlab.helper;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DistributedLockHelper {

    private final RedissonClient redissonClient;

    /**
     * 분산락을 획득하고 작업 실행
     *
     * @param lockKey   락의 고유 키 (예: "order:product:123")
     * @param waitTime  락 획득 대기 시간 (초)
     * @param leaseTime 락 자동 해제 시간 (초) - 데드락 방지
     * @param task      실행할 작업
     */
    public <T> T executeWithLock(
            String lockKey,
            long waitTime,
            long leaseTime,
            Supplier<T> task) {

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!acquired) {
                throw new IllegalStateException(
                        "락을 획득하지 못했습니다: " + lockKey
                );
            }

            // 락 획득 성공 - 작업 실행
            return task.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 획득 중 인터럽트 발생", e);
        } finally {
            // 락 해제 (현재 스레드가 락을 보유한 경우만)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
