package com.choo.hhbackendlab.redis;

import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserCouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis Sorted Set을 활용한 선착순 쿠폰 발급 서비스
 *
 * 주요 기능:
 * 1. Redis Sorted Set에 발급 요청을 타임스탬프 순으로 저장
 * 2. Redis String으로 중복 발급 방지
 * 3. 원자적 연산으로 동시성 보장
 * 4. TTL 설정으로 자동 만료
 *
 * 키 구조:
 * - cpn:wl:{couponName} : Sorted Set (userId를 member로, timestamp를 score로 저장)
 * - cpn:isu:{couponName}:{userId} : String (발급 완료 여부 체크)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssue {

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;

    // Redis 키 접두사 (메모리 최적화)
    private static final String WAITING_LIST_KEY_PREFIX = "cpn:wl:";   // 발급 대기열 (Sorted Set)
    private static final String ISSUED_KEY_PREFIX = "cpn:isu:";        // 발급 완료

    // TTL 설정 (7일)
    private static final long TTL_DAYS = 7;

    /**
     * 선착순 쿠폰 발급 요청을 Redis Sorted Set에 추가
     * Redis Pipeline을 사용하여 중복 체크, 대기열 추가, TTL 설정을 원자적으로 처리
     *
     * @param userId 사용자 ID
     * @param couponName 쿠폰 이름
     * @return 대기열 순서 (0부터 시작)
     */
    @Transactional
    public Long addToWaitingList(Long userId, String couponName) {
        try {
            String waitingListKey = WAITING_LIST_KEY_PREFIX + couponName;
            String issuedKey = ISSUED_KEY_PREFIX + couponName + ":" + userId;

            // 1. 이미 발급받은 사용자인지 확인 (Redis 체크)
            Boolean alreadyIssued = redisTemplate.hasKey(issuedKey);
            if (Boolean.TRUE.equals(alreadyIssued)) {
                log.warn("이미 발급받은 쿠폰 - userId: {}, couponName: {}", userId, couponName);
                throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            }

            // 2. DB에서도 중복 체크 (Redis 장애 대비)
            Coupon coupon = couponRepository.findFirstAvailableCouponByName(couponName)
                    .orElseThrow(() -> new IllegalStateException("발급 가능한 쿠폰이 없습니다. 쿠폰명: " + couponName));

            boolean alreadyIssuedInDB = userCouponRepository.existsByUserIdAndCouponId(userId, coupon.getId());
            if (alreadyIssuedInDB) {
                log.warn("DB에 이미 발급 기록 존재 - userId: {}, couponId: {}", userId, coupon.getId());

                // Redis에도 발급 완료 마킹
                redisTemplate.opsForValue().set(issuedKey, "1", TTL_DAYS, TimeUnit.DAYS);
                throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            }

            // 3. Redis Pipeline으로 원자적 처리 (대기열 추가 + TTL 설정)
            double score = System.nanoTime();

            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    ZSetOperations<String, String> zSetOps = operations.opsForZSet();

                    // Sorted Set에 추가
                    zSetOps.add(waitingListKey, userId.toString(), score);

                    // TTL 설정
                    operations.expire(waitingListKey, TTL_DAYS, TimeUnit.DAYS);

                    return null;
                }
            });

            // 4. 대기열 추가 확인
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
            Double addedScore = zSetOps.score(waitingListKey, userId.toString());

            if (addedScore == null) {
                log.error("대기열 추가 실패 - userId: {}, couponName: {}", userId, couponName);
                throw new IllegalStateException("대기열 추가에 실패했습니다.");
            }

            // 5. 대기열 순서 조회
            Long rank = zSetOps.rank(waitingListKey, userId.toString());

            log.info("쿠폰 발급 대기열 추가 완료 - userId: {}, couponName: {}, rank: {}, score: {}",
                    userId, couponName, rank, score);

            return rank != null ? rank : 0L;

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("쿠폰 발급 대기열 추가 실패 - userId: {}, couponName: {}", userId, couponName, e);
            throw new RuntimeException("쿠폰 발급 요청 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * Sorted Set에서 가장 오래된(가장 작은 score) 요청 하나를 처리
     *
     * @return 처리 성공 여부
     */
    @Transactional
    public boolean processNextRequest() {
        try {
            // 모든 쿠폰의 대기열을 처리하기 위해 쿠폰 이름 목록을 가져와야 하지만,
            // 현재는 단순화를 위해 특정 패턴의 키들을 찾아서 처리
            Set<String> waitingListKeys = redisTemplate.keys(WAITING_LIST_KEY_PREFIX + "*");

            if (waitingListKeys == null || waitingListKeys.isEmpty()) {
                log.debug("처리할 대기열이 없습니다.");
                return false;
            }

            // 각 쿠폰의 대기열에서 하나씩 처리
            boolean processed = false;
            for (String waitingListKey : waitingListKeys) {
                String couponName = waitingListKey.substring(WAITING_LIST_KEY_PREFIX.length());
                if (processNextInWaitingList(couponName)) {
                    processed = true;
                    break; // 하나 처리했으면 종료
                }
            }

            return processed;

        } catch (Exception e) {
            log.error("대기열 처리 중 오류 발생", e);
            return false;
        }
    }

    /**
     * 특정 쿠폰의 Sorted Set에서 가장 앞의 요청 하나를 처리
     *
     * @param couponName 쿠폰 이름
     * @return 처리 성공 여부
     */
    @Transactional
    public boolean processNextInWaitingList(String couponName) {
        String waitingListKey = WAITING_LIST_KEY_PREFIX + couponName;

        try {
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

            // 1. 가장 앞의 요청 하나를 가져오기
            Set<String> members = zSetOps.range(waitingListKey, 0, 0);

            if (members == null || members.isEmpty()) {
                log.debug("처리할 대기열 요청 없음 - couponName: {}", couponName);
                return false;
            }

            String userIdStr = members.iterator().next();
            Long userId = Long.parseLong(userIdStr);

            // 2. Sorted Set에서 제거 (처리 시작)
            Long removed = zSetOps.remove(waitingListKey, userIdStr);

            if (removed == null || removed == 0) {
                log.debug("다른 프로세스에서 이미 처리됨 - userId: {}, couponName: {}", userId, couponName);
                return false;
            }

            log.info("쿠폰 발급 처리 시작 - userId: {}, couponName: {}", userId, couponName);

            // 3. 실제 쿠폰 발급
            Long issuedCouponId = issueCoupon(userId, couponName);

            // 4. 발급 완료 마킹 (Redis에 추가)
            String issuedKey = ISSUED_KEY_PREFIX + couponName + ":" + userId;
            redisTemplate.opsForValue().set(issuedKey, "1", TTL_DAYS, TimeUnit.DAYS);

            log.info("쿠폰 발급 완료 - userId: {}, couponName: {}, issuedCouponId: {}",
                    userId, couponName, issuedCouponId);

            return true;

        } catch (Exception e) {
            log.error("쿠폰 발급 처리 실패 - couponName: {}", couponName, e);
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

        // 재고 감소 (원자적 업데이트)
        int updatedRows = couponRepository.decreaseCouponCount(coupon.getId());
        if (updatedRows == 0) {
            throw new IllegalStateException("쿠폰 재고 부족 - couponName: " + couponName);
        }

        // 쿠폰 발급 (UserCoupon 생성)
        UserCoupon userCoupon = coupon.issueCoupon(user);

        // UserCoupon 저장
        UserCoupon saved = userCouponRepository.save(userCoupon);

        return saved.getId();
    }

    /**
     * 대기 중인 발급 요청 개수 조회
     */
    public long getPendingRequestCount() {
        try {
            Set<String> waitingListKeys = redisTemplate.keys(WAITING_LIST_KEY_PREFIX + "*");
            if (waitingListKeys == null || waitingListKeys.isEmpty()) {
                return 0;
            }

            long totalCount = 0;
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
            for (String waitingListKey : waitingListKeys) {
                Long size = zSetOps.size(waitingListKey);
                totalCount += (size != null ? size : 0);
            }

            return totalCount;
        } catch (Exception e) {
            log.error("대기열 개수 조회 실패", e);
            return 0;
        }
    }

    /**
     * 특정 쿠폰의 대기열 크기 조회
     *
     * @param couponName 쿠폰 이름
     * @return 대기 중인 요청 개수
     */
    public Long getWaitingListSize(String couponName) {
        String waitingListKey = WAITING_LIST_KEY_PREFIX + couponName;
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Long size = zSetOps.size(waitingListKey);
        return size != null ? size : 0L;
    }

    /**
     * 사용자의 대기열 순서 조회
     *
     * @param userId 사용자 ID
     * @param couponName 쿠폰 이름
     * @return 대기 순서 (0부터 시작, 없으면 -1)
     */
    public Long getUserWaitingPosition(Long userId, String couponName) {
        String waitingListKey = WAITING_LIST_KEY_PREFIX + couponName;
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Long rank = zSetOps.rank(waitingListKey, userId.toString());
        return rank != null ? rank : -1L;
    }

    /**
     * 대기열 초기화 (관리자용)
     *
     * @param couponName 쿠폰 이름
     */
    public void clearWaitingList(String couponName) {
        String waitingListKey = WAITING_LIST_KEY_PREFIX + couponName;
        redisTemplate.delete(waitingListKey);
        log.info("대기열 초기화 완료 - couponName: {}", couponName);
    }

    /**
     * 발급 완료 기록 초기화 (관리자용, 테스트용)
     *
     * @param couponName 쿠폰 이름
     */
    public void clearIssuedRecords(String couponName) {
        String pattern = ISSUED_KEY_PREFIX + couponName + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("발급 완료 기록 초기화 완료 - couponName: {}, count: {}", couponName, keys.size());
        }
    }
}