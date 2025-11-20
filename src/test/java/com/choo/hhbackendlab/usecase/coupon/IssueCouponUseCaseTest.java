package com.choo.hhbackendlab.usecase.coupon;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.entity.UserCoupon;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserCouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
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

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IssueCouponUseCase 통합 테스트
 * TestContainer를 사용하여 실제 MySQL 환경에서 쿠폰 발급 기능을 검증
 */
@SpringBootTest
@Testcontainers
public class IssueCouponUseCaseTest {

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

    @BeforeEach
    void setUp() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("10명의 유저에게 1000원 쿠폰 1장씩 발급 성공")
    void issueCouponToTenUsers_Success() {
        // Given
        // 1. 10명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            User user = createUser("user" + i, "user" + i + "@test.com", "password123");
            users.add(userRepository.save(user));
        }

        // 2. 1000원 쿠폰 템플릿 생성 (발급 가능 개수: 10개)
        Coupon coupon = new Coupon(
                "1000원 할인 쿠폰",
                10,  // 발급 가능 개수
                1000,  // 할인 금액
                0,  // 최소 주문 금액 (제한 없음)
                LocalDateTime.now().plusDays(30)
        );
        Coupon savedCoupon = couponRepository.save(coupon);

        // When
        // 10명의 유저에게 각각 쿠폰 발급
        List<Long> issuedCouponIds = new ArrayList<>();
        for (User user : users) {
            Long userCouponId = issueCouponUseCase.issueCoupon(user.getId(), savedCoupon.getId());
            issuedCouponIds.add(userCouponId);
        }

        // Then
        // 1. 10개의 쿠폰이 발급되었는지 확인
        assertThat(issuedCouponIds).hasSize(10);
        assertThat(issuedCouponIds).doesNotContainNull();

        // 2. 각 유저에게 쿠폰이 정상 발급되었는지 확인
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            Long userCouponId = issuedCouponIds.get(i);

            // UserCoupon 조회
            UserCoupon userCoupon = userCouponRepository.findById(userCouponId).orElseThrow();

            // 쿠폰 정보 확인
            assertThat(userCoupon.getUser().getId()).isEqualTo(user.getId());
            assertThat(userCoupon.isUsed()).isFalse();
            assertThat(userCoupon.getUsedAt()).isNull();
            assertThat(userCoupon.getCouponCode()).isNotNull();
        }

        // 3. 쿠폰 템플릿 정보 확인
        Coupon updatedCoupon = couponRepository.findById(savedCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getRemainingCount()).isEqualTo(0);  // 발급 가능 개수가 0
        assertThat(updatedCoupon.getCouponAmount()).isEqualTo(1000);  // 쿠폰 금액 1000원
        assertThat(updatedCoupon.getName()).isEqualTo("1000원 할인 쿠폰");

        // 4. DB에 총 10개의 UserCoupon이 저장되었는지 확인
        List<UserCoupon> allUserCoupons = userCouponRepository.findAll();
        assertThat(allUserCoupons).hasSize(10);

        // 5. 각 유저별로 쿠폰이 1개씩만 발급되었는지 확인
        for (User user : users) {
            List<UserCoupon> userCoupons = userCouponRepository.findAll().stream()
                    .filter(uc -> uc.getUser().getId().equals(user.getId()))
                    .toList();
            assertThat(userCoupons).hasSize(1);
        }
    }

    /**
     * User 생성 헬퍼 메서드 (Reflection 사용)
     */
    private User createUser(String username, String email, String password) {
        User user = new User();
        setField(user, "username", username);
        setField(user, "email", email);
        setField(user, "password", password);
        setField(user, "registerAt", LocalDateTime.now());
        return user;
    }

    /**
     * Reflection을 사용하여 private 필드에 값을 설정하는 헬퍼 메서드
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("필드 설정 실패: " + fieldName, e);
        }
    }
}
