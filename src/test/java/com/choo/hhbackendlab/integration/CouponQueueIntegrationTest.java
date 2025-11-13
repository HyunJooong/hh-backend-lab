package com.choo.hhbackendlab.integration;

import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.repository.CouponIssueQueueRepository;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserCouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import com.choo.hhbackendlab.usecase.coupon.IssueCouponUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Queue 기반 선착순 쿠폰 발급 통합 테스트
 * TestContainer를 사용하여 실제 MySQL 환경에서 동시성 제어를 검증
 */
@SpringBootTest
@Testcontainers
public class CouponQueueIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("hh_backend_lab")
            .withUsername("root")
            .withPassword("1234");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponIssueQueueRepository queueRepository;

    private static final String COUPON_NAME = "선착순 100명 할인 쿠폰";
    private static final int COUPON_COUNT = 100; // 발급할 쿠폰 개수
    private static final int USER_COUNT = 50; // 동시 요청 사용자 수

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        queueRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();

        // 50명의 사용자 생성
        for (int i = 1; i <= USER_COUNT; i++) {
            User user = new User();
            userRepository.save(user);
        }

        // 쿠폰 템플릿 1개 생성 (100개 발급 가능)
        Coupon coupon = new Coupon(
                COUPON_NAME,
                COUPON_COUNT, // 발급 가능 개수: 100개
                10000, // 10,000원 할인
                50000, // 최소 주문 금액 50,000원
                LocalDateTime.now().plusDays(30)
        );
        couponRepository.save(coupon);
    }

    @Test
    @DisplayName("Queue 기반 선착순 쿠폰 발급 - 50명이 동시 요청하면 모두 Queue에 등록되고 순차적으로 처리됨")
    void issueCouponWithQueue_ConcurrentRequests_Success() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch latch = new CountDownLatch(USER_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(USER_COUNT);

        // when - 50명이 동시에 쿠폰 발급 요청
        for (int i = 0; i < USER_COUNT; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    Long userId = users.get(index).getId();
                    Long queueId = issueCouponUseCase.issueCouponByName(userId, COUPON_NAME);
                    assertThat(queueId).isNotNull();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Queue 등록 실패: " + e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then - 모든 요청이 Queue에 등록되었는지 확인
        assertThat(successCount.get()).isEqualTo(USER_COUNT);
        assertThat(failCount.get()).isEqualTo(0);

        List<CouponIssueQueue> allQueues = queueRepository.findAll();
        assertThat(allQueues).hasSize(USER_COUNT);

        // 스케줄러가 Queue를 처리할 시간을 대기
        // 100ms마다 하나씩 처리하므로 50개 처리에 약 5초 + 여유시간
        System.out.println("=== 스케줄러가 Queue를 처리하는 중입니다... ===");
        waitForQueueProcessing(USER_COUNT, 15000); // 최대 15초 대기

        // 최종 검증
        List<CouponIssueQueue> completedQueues = queueRepository.findByStatus(QueueStatus.COMPLETED);
        List<CouponIssueQueue> failedQueues = queueRepository.findByStatus(QueueStatus.FAILED);
        List<CouponIssueQueue> pendingQueues = queueRepository.findByStatus(QueueStatus.PENDING);

        System.out.println("=== Queue 처리 결과 ===");
        System.out.println("완료: " + completedQueues.size());
        System.out.println("실패: " + failedQueues.size());
        System.out.println("대기: " + pendingQueues.size());

        // 50개 모두 완료되어야 함
        assertThat(completedQueues).hasSize(USER_COUNT);
        assertThat(failedQueues).isEmpty();
        assertThat(pendingQueues).isEmpty();

        // 실제로 발급된 UserCoupon 확인
        List<UserCoupon> issuedCoupons = userCouponRepository.findAll();

        assertThat(issuedCoupons).hasSize(USER_COUNT);

        // Coupon 템플릿의 남은 발급 가능 개수 확인
        Coupon coupon = couponRepository.findByName(COUPON_NAME).get(0);
        assertThat(coupon.getRemainingCount()).isEqualTo(COUPON_COUNT - USER_COUNT);

        System.out.println("=== 쿠폰 발급 결과 ===");
        System.out.println("발급된 쿠폰: " + issuedCoupons.size());
        System.out.println("남은 발급 가능 개수: " + coupon.getRemainingCount());
    }

    @Test
    @DisplayName("Queue 기반 선착순 쿠폰 발급 - 각 사용자는 하나의 쿠폰만 받아야 함")
    void issueCouponWithQueue_OnePerUser() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch latch = new CountDownLatch(USER_COUNT);

        List<User> users = userRepository.findAll();

        // when - 50명이 동시에 쿠폰 발급 요청
        for (int i = 0; i < USER_COUNT; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    Long userId = users.get(index).getId();
                    issueCouponUseCase.issueCouponByName(userId, COUPON_NAME);
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // 스케줄러가 Queue를 처리할 시간을 대기
        waitForQueueProcessing(USER_COUNT, 15000);

        // then - 각 사용자는 최대 1개의 쿠폰만 소유해야 함
        for (User user : users) {
            List<UserCoupon> userCoupons = userCouponRepository.findByUser(user);
            assertThat(userCoupons.size()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Queue 기반 선착순 쿠폰 발급 - 쿠폰이 모두 소진되면 나머지 요청은 실패해야 함")
    void issueCouponWithQueue_OutOfStock() throws InterruptedException {
        // given - 쿠폰 10개만 발급 가능하도록 설정
        couponRepository.deleteAll();
        queueRepository.deleteAll();
        userCouponRepository.deleteAll();

        // 쿠폰 템플릿 1개 생성 (10개만 발급 가능)
        Coupon coupon = new Coupon(
                COUPON_NAME,
                10, // 발급 가능 개수: 10개
                10000, // 10,000원 할인
                50000, // 최소 주문 금액 50,000원
                LocalDateTime.now().plusDays(30)
        );
        couponRepository.save(coupon);

        ExecutorService executorService = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch latch = new CountDownLatch(USER_COUNT);

        List<User> users = userRepository.findAll();

        // when - 50명이 10개의 쿠폰을 요청
        for (int i = 0; i < USER_COUNT; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    Long userId = users.get(index).getId();
                    issueCouponUseCase.issueCouponByName(userId, COUPON_NAME);
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // 스케줄러가 Queue를 처리할 시간을 대기
        waitForQueueProcessing(USER_COUNT, 20000); // 더 긴 대기 시간

        // then
        List<CouponIssueQueue> completedQueues = queueRepository.findByStatus(QueueStatus.COMPLETED);
        List<CouponIssueQueue> failedQueues = queueRepository.findByStatus(QueueStatus.FAILED);

        System.out.println("=== 쿠폰 소진 테스트 결과 ===");
        System.out.println("완료: " + completedQueues.size());
        System.out.println("실패: " + failedQueues.size());

        // 10개만 성공, 나머지는 실패해야 함
        assertThat(completedQueues).hasSize(10);
        assertThat(failedQueues).hasSize(40);

        // 실패 사유 확인
        assertThat(failedQueues).allMatch(queue ->
                queue.getErrorMessage().contains("발급 가능한 쿠폰이 없습니다")
        );
    }

    /**
     * Queue 처리가 완료될 때까지 대기
     * @param expectedCount 예상 처리 건수
     * @param maxWaitMillis 최대 대기 시간 (밀리초)
     */
    private void waitForQueueProcessing(int expectedCount, long maxWaitMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long processedCount = 0;

        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            processedCount = queueRepository.findByStatus(QueueStatus.COMPLETED).size()
                    + queueRepository.findByStatus(QueueStatus.FAILED).size();

            System.out.println("처리된 Queue: " + processedCount + " / " + expectedCount);

            if (processedCount >= expectedCount) {
                System.out.println("모든 Queue 처리 완료!");
                return;
            }

            Thread.sleep(500); // 0.5초마다 확인
        }

        System.out.println("타임아웃: " + processedCount + "개 처리됨");
    }
}