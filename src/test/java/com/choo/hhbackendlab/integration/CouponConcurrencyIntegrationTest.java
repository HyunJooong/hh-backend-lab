package com.choo.hhbackendlab.integration;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import com.choo.hhbackendlab.usecase.coupon.IssueCouponUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 발급 동시성 제어 통합 테스트
 * synchronized를 사용한 동시성 제어가 정상적으로 작동하는지 검증
 */
@SpringBootTest
public class CouponConcurrencyIntegrationTest {

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    private static final String COUPON_NAME = "선착순 테스트 쿠폰";
    private static final int COUPON_COUNT = 10; // 발급할 쿠폰 개수
    private static final int THREAD_COUNT = 30; // 동시 요청 스레드 수

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        couponRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 사용자 생성 (30명)
        for (int i = 1; i <= THREAD_COUNT; i++) {
            User user = new User();
            userRepository.save(user);
        }

        // 쿠폰 10개 생성
        for (int i = 0; i < COUPON_COUNT; i++) {
            Coupon coupon = new Coupon(
                    COUPON_NAME,
                    COUPON_COUNT,
                    10000,
                    50000,
                    LocalDateTime.now().plusDays(30)
            );
            couponRepository.save(coupon);
        }
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 동시에 30명이 요청했을 때 10명만 성공해야 함")
    void issueCouponConcurrency_Success() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<User> users = userRepository.findAll();

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    Long userId = users.get(index).getId();
                    issueCouponUseCase.issueCouponByName(userId, COUPON_NAME);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    // 발급 가능한 쿠폰이 없습니다.
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("예상치 못한 예외: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(COUPON_COUNT); // 10개만 성공
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - COUPON_COUNT); // 20개 실패

        // 실제로 발급된 쿠폰 확인
        List<Coupon> issuedCoupons = couponRepository.findAll().stream()
                .filter(coupon -> coupon.getUser() != null)
                .toList();

        assertThat(issuedCoupons).hasSize(COUPON_COUNT);
        assertThat(issuedCoupons).allMatch(Coupon::isIssued);

        System.out.println("=== 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());
        System.out.println("발급된 쿠폰: " + issuedCoupons.size());
    }


    @Test
    @DisplayName("선착순 쿠폰 발급 - 각 사용자는 하나의 쿠폰만 받아야 함")
    void issueCouponConcurrency_OnePerUser() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        List<User> users = userRepository.findAll();

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
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

        // then
        // 각 사용자는 최대 1개의 쿠폰만 소유해야 함
        for (User user : users) {
            List<Coupon> userCoupons = couponRepository.findByUser(user);
            assertThat(userCoupons.size()).isLessThanOrEqualTo(1);
        }
    }
}
