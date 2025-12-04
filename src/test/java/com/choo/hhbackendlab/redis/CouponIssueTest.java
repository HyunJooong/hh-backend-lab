package com.choo.hhbackendlab.redis;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.entity.UserCoupon;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserCouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import com.choo.hhbackendlab.scheduler.CouponIssueProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CouponIssue Redis Pipeline 및 비동기 처리 통합 테스트
 * Redis Sorted Set 기반 선착순 쿠폰 발급 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CouponIssueTest {

    @Autowired
    private CouponIssue couponIssue;

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    private static final String WAITING_LIST_KEY_PREFIX = "cpn:wl:";
    private static final String ISSUED_KEY_PREFIX = "cpn:isu:";
    private static final String TEST_COUPON_NAME = "신규가입쿠폰";

    private User user1;
    private User user2;
    private Coupon coupon;

    @BeforeEach
    void setUp() throws Exception {
        // Redis 초기화
        clearAllCouponData();

        // 기존 DB 데이터 정리
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트 유저 생성
        user1 = createUser("사용자1", "user1@test.com");
        userRepository.save(user1);

        user2 = createUser("사용자2", "user2@test.com");
        userRepository.save(user2);

        // 테스트 쿠폰 생성
        coupon = createCoupon(TEST_COUPON_NAME, 100, 10000);
        couponRepository.save(coupon);
    }

    @Test
    @DisplayName("대기열 추가 - Redis Pipeline으로 원자적 처리 검증")
    void addToWaitingList_WithPipeline_Success() {
        // given
        String couponName = TEST_COUPON_NAME;

        // when
        Long rank = couponIssue.addToWaitingList(user1.getId(), couponName);

        // then
        assertThat(rank).isEqualTo(0L); // 첫 번째 순서

        // Redis Sorted Set 확인
        String waitingListKey = WAITING_LIST_KEY_PREFIX + couponName;
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Long size = zSetOps.size(waitingListKey);
        assertThat(size).isEqualTo(1L);

        // TTL 설정 확인
        Long ttl = redisTemplate.getExpire(waitingListKey);
        assertThat(ttl).isGreaterThan(0L);
    }

    @Test
    @DisplayName("여러 사용자 대기열 추가 시 순서 보장")
    void addToWaitingList_MultipleUsers_OrderGuaranteed() throws InterruptedException {
        // given
        String couponName = TEST_COUPON_NAME;

        // when - 순차적으로 추가
        Long rank1 = couponIssue.addToWaitingList(user1.getId(), couponName);
        Thread.sleep(1); // 나노초 차이 보장
        Long rank2 = couponIssue.addToWaitingList(user2.getId(), couponName);

        // then - 순서 확인
        assertThat(rank1).isEqualTo(0L);
        assertThat(rank2).isEqualTo(1L);

        // 대기열 크기 확인
        Long waitingListSize = couponIssue.getWaitingListSize(couponName);
        assertThat(waitingListSize).isEqualTo(2L);
    }

    @Test
    @DisplayName("중복 발급 방지 - Redis 체크")
    void addToWaitingList_DuplicateCheck_Redis() {
        // given
        String couponName = TEST_COUPON_NAME;
        String issuedKey = ISSUED_KEY_PREFIX + couponName + ":" + user1.getId();

        // 이미 발급된 것으로 마킹
        redisTemplate.opsForValue().set(issuedKey, "1");

        // when & then
        assertThatThrownBy(() -> couponIssue.addToWaitingList(user1.getId(), couponName))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 발급받은 쿠폰입니다");
    }

    @Test
    @DisplayName("중복 발급 방지 - DB 체크")
    void addToWaitingList_DuplicateCheck_Database() {
        // given
        String couponName = TEST_COUPON_NAME;

        // DB에 이미 발급 기록 생성
        UserCoupon userCoupon = coupon.issueCoupon(user1);
        userCouponRepository.save(userCoupon);

        // when & then
        assertThatThrownBy(() -> couponIssue.addToWaitingList(user1.getId(), couponName))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 발급받은 쿠폰입니다");

        // Redis에도 마킹되었는지 확인
        String issuedKey = ISSUED_KEY_PREFIX + couponName + ":" + user1.getId();
        Boolean exists = redisTemplate.hasKey(issuedKey);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("비동기 처리 - 대기열 처리 후 실제 쿠폰 발급까지 검증")
    void processNextInWaitingList_AsyncIssueCoupon_Success() throws InterruptedException {
        // given
        String couponName = TEST_COUPON_NAME;
        couponIssue.addToWaitingList(user1.getId(), couponName);

        // when - 비동기 처리 (스케줄러 호출)
        boolean processed = couponIssue.processNextInWaitingList(couponName);

        // then
        assertThat(processed).isTrue();

        // 대기열에서 제거 확인
        Long waitingListSize = couponIssue.getWaitingListSize(couponName);
        assertThat(waitingListSize).isEqualTo(0L);

        // 발급 완료 마킹 확인
        String issuedKey = ISSUED_KEY_PREFIX + couponName + ":" + user1.getId();
        Boolean issued = redisTemplate.hasKey(issuedKey);
        assertThat(issued).isTrue();

        // DB에 UserCoupon 생성 확인
        boolean existsInDB = userCouponRepository.existsByUserIdAndCouponId(user1.getId(), coupon.getId());
        assertThat(existsInDB).isTrue();

        // 쿠폰 재고 감소 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getRemainingCount()).isEqualTo(99);
    }

    @Test
    @DisplayName("스케줄러 비동기 처리 - 여러 건 순차 처리 검증")
    void schedulerProcessing_MultipleRequests() throws Exception {
        // given
        String couponName = TEST_COUPON_NAME;
        int userCount = 5;
        List<User> users = new ArrayList<>();

        for (int i = 0; i < userCount; i++) {
            User user = createUser("사용자" + (i + 3), "user" + (i + 3) + "@test.com");
            userRepository.save(user);
            users.add(user);
            couponIssue.addToWaitingList(user.getId(), couponName);
        }

        // 대기열 크기 확인
        Long waitingListSize = couponIssue.getWaitingListSize(couponName);
        assertThat(waitingListSize).isEqualTo(userCount);

        // when - 스케줄러 수동 실행 (비동기 처리 시뮬레이션)
        for (int i = 0; i < userCount; i++) {
            couponIssueProcessor.processWaitingList();
            Thread.sleep(10); // 스케줄러 간격 시뮬레이션
        }

        // then - 모든 요청 처리 확인
        waitingListSize = couponIssue.getWaitingListSize(couponName);
        assertThat(waitingListSize).isEqualTo(0L);

        // 모든 사용자에게 쿠폰 발급 확인
        List<UserCoupon> issuedCoupons = userCouponRepository.findAll();
        assertThat(issuedCoupons).hasSize(userCount);

        // 쿠폰 재고 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getRemainingCount()).isEqualTo(100 - userCount);
    }

    @Test
    @DisplayName("동시성 테스트 - 100명이 동시 요청 시 대기열 순서 보장")
    void concurrency_100Users_QueueOrderGuaranteed() throws Exception {
        // given
        String couponName = TEST_COUPON_NAME;
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        List<User> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            User user = createUser("동시사용자" + i, "concurrent" + i + "@test.com");
            userRepository.save(user);
            users.add(user);
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명 동시 대기열 추가
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    couponIssue.addToWaitingList(users.get(index).getId(), couponName);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then - 모든 요청 성공
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 대기열 크기 확인
        Long waitingListSize = couponIssue.getWaitingListSize(couponName);
        assertThat(waitingListSize).isEqualTo(threadCount);

        // Sorted Set에 모든 데이터 존재 확인
        String waitingListKey = WAITING_LIST_KEY_PREFIX + couponName;
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Set<String> allMembers = zSetOps.range(waitingListKey, 0, -1);
        assertThat(allMembers).hasSize(threadCount);

        System.out.println("100명 동시 요청 성공: 대기열 크기 = " + waitingListSize);
    }

    @Test
    @DisplayName("쿠폰 소진 시나리오 - 10개 쿠폰에 50명 요청")
    void couponExhaustion_10Coupons_50Requests() throws Exception {
        // given - 10개만 발급 가능한 쿠폰 생성
        couponRepository.deleteAll();
        clearAllCouponData();

        Coupon limitedCoupon = createCoupon("한정쿠폰", 10, 5000);
        couponRepository.save(limitedCoupon);

        int userCount = 50;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            User user = createUser("한정사용자" + i, "limited" + i + "@test.com");
            userRepository.save(user);
            users.add(user);
        }

        // when - 50명이 대기열 추가
        for (User user : users) {
            try {
                couponIssue.addToWaitingList(user.getId(), "한정쿠폰");
            } catch (Exception e) {
                // 예외 무시
            }
        }

        // 대기열 전체 처리
        int processedCount = 0;
        int maxAttempts = 60; // 최대 60번 시도

        while (processedCount < maxAttempts) {
            boolean processed = couponIssue.processNextInWaitingList("한정쿠폰");
            if (!processed) {
                break; // 더 이상 처리할 요청 없음
            }
            processedCount++;
            Thread.sleep(10);
        }

        // then - 10개만 발급되어야 함
        List<UserCoupon> issuedCoupons = userCouponRepository.findAll();
        assertThat(issuedCoupons.size()).isLessThanOrEqualTo(10);

        // 쿠폰 재고 확인
        Coupon updatedCoupon = couponRepository.findById(limitedCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getRemainingCount()).isGreaterThanOrEqualTo(0);

        System.out.println("발급된 쿠폰: " + issuedCoupons.size() + " / 10");
        System.out.println("남은 재고: " + updatedCoupon.getRemainingCount());
    }

    @Test
    @DisplayName("대기열 순서 조회")
    void getUserWaitingPosition_Success() throws InterruptedException {
        // given
        String couponName = TEST_COUPON_NAME;
        couponIssue.addToWaitingList(user1.getId(), couponName);
        Thread.sleep(1);
        couponIssue.addToWaitingList(user2.getId(), couponName);

        // when
        Long position1 = couponIssue.getUserWaitingPosition(user1.getId(), couponName);
        Long position2 = couponIssue.getUserWaitingPosition(user2.getId(), couponName);

        // then
        assertThat(position1).isEqualTo(0L);
        assertThat(position2).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 대기열 순서 조회 시 -1 반환")
    void getUserWaitingPosition_NotExists() {
        // given
        String couponName = TEST_COUPON_NAME;
        Long nonExistentUserId = 99999L;

        // when
        Long position = couponIssue.getUserWaitingPosition(nonExistentUserId, couponName);

        // then
        assertThat(position).isEqualTo(-1L);
    }

    @Test
    @DisplayName("대기 중인 발급 요청 개수 조회")
    void getPendingRequestCount_Success() {
        // given
        String couponName = TEST_COUPON_NAME;
        couponIssue.addToWaitingList(user1.getId(), couponName);
        couponIssue.addToWaitingList(user2.getId(), couponName);

        // when
        long pendingCount = couponIssue.getPendingRequestCount();

        // then
        assertThat(pendingCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("대기열 초기화")
    void clearWaitingList_Success() {
        // given
        String couponName = TEST_COUPON_NAME;
        couponIssue.addToWaitingList(user1.getId(), couponName);
        couponIssue.addToWaitingList(user2.getId(), couponName);

        // when
        couponIssue.clearWaitingList(couponName);

        // then
        Long waitingListSize = couponIssue.getWaitingListSize(couponName);
        assertThat(waitingListSize).isEqualTo(0L);
    }

    @Test
    @DisplayName("발급 완료 기록 초기화")
    void clearIssuedRecords_Success() {
        // given
        String couponName = TEST_COUPON_NAME;
        String issuedKey1 = ISSUED_KEY_PREFIX + couponName + ":" + user1.getId();
        String issuedKey2 = ISSUED_KEY_PREFIX + couponName + ":" + user2.getId();

        redisTemplate.opsForValue().set(issuedKey1, "1");
        redisTemplate.opsForValue().set(issuedKey2, "1");

        // when
        couponIssue.clearIssuedRecords(couponName);

        // then
        Boolean exists1 = redisTemplate.hasKey(issuedKey1);
        Boolean exists2 = redisTemplate.hasKey(issuedKey2);
        assertThat(exists1).isFalse();
        assertThat(exists2).isFalse();
    }

    @Test
    @DisplayName("빈 대기열 처리 시 false 반환")
    void processNextRequest_EmptyQueue() {
        // given - 대기열 없음

        // when
        boolean processed = couponIssue.processNextRequest();

        // then
        assertThat(processed).isFalse();
    }

    /**
     * 모든 쿠폰 관련 Redis 데이터 초기화
     */
    private void clearAllCouponData() {
        Set<String> waitingKeys = redisTemplate.keys(WAITING_LIST_KEY_PREFIX + "*");
        if (waitingKeys != null && !waitingKeys.isEmpty()) {
            redisTemplate.delete(waitingKeys);
        }

        Set<String> issuedKeys = redisTemplate.keys(ISSUED_KEY_PREFIX + "*");
        if (issuedKeys != null && !issuedKeys.isEmpty()) {
            redisTemplate.delete(issuedKeys);
        }
    }

    /**
     * User 생성 (Reflection 사용)
     */
    private User createUser(String username, String email) throws Exception {
        User user = new User();
        setField(user, "username", username);
        setField(user, "email", email);
        return user;
    }

    /**
     * Coupon 생성 (생성자 사용)
     */
    private Coupon createCoupon(String name, int couponCnt, int discountAmount) {
        return new Coupon(
                name,
                couponCnt,
                discountAmount,
                0, // minOrderAmount
                java.time.LocalDateTime.now().plusDays(30) // expiredAt
        );
    }

    /**
     * Reflection으로 private 필드 설정
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}