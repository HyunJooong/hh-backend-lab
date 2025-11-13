package com.choo.hhbackendlab.usecase.coupon;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class IssueCouponUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private IssueCouponUseCase issueCouponUseCase;

    @Test
    @DisplayName("쿠폰 발급 성공 - 특정 쿠폰 ID로 발급")
    void issueCoupon_Success() throws Exception {
        // given
        Long userId = 1L;
        Long couponId = 100L;

        User user = createUser(userId, "testuser");
        Coupon coupon = createCoupon(couponId, "신규가입 쿠폰", 100, 10000, 50000, LocalDateTime.now().plusDays(30));

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        // when
        issueCouponUseCase.issueCoupon(userId, couponId);

        // then
        assertThat(coupon.isIssued()).isTrue();
        assertThat(coupon.getUser()).isEqualTo(user);
        assertThat(coupon.getIssuedAt()).isNotNull();

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 사용자 ID")
    void issueCoupon_UserNotFound() {
        // given
        Long invalidUserId = 999L;
        Long couponId = 100L;

        given(userRepository.findById(invalidUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.issueCoupon(invalidUserId, couponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verify(userRepository).findById(invalidUserId);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 쿠폰 ID")
    void issueCoupon_CouponNotFound() throws Exception {
        // given
        Long userId = 1L;
        Long invalidCouponId = 999L;

        User user = createUser(userId, "testuser");

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(couponRepository.findById(invalidCouponId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.issueCoupon(userId, invalidCouponId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(invalidCouponId);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 성공 - 쿠폰 이름으로 발급")
    void issueCouponByName_Success() throws Exception {
        // given
        Long userId = 1L;
        String couponName = "신규가입 쿠폰";

        User user = createUser(userId, "testuser");
        Coupon coupon = createCoupon(100L, couponName, 100, 10000, 50000, LocalDateTime.now().plusDays(30));

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(couponRepository.findFirstUnissuedCouponByNameWithLock(couponName)).willReturn(Optional.of(coupon));

        // when
        Long issuedCouponId = issueCouponUseCase.issueCouponByName(userId, couponName);

        // then
        assertThat(issuedCouponId).isEqualTo(100L);
        assertThat(coupon.isIssued()).isTrue();
        assertThat(coupon.getUser()).isEqualTo(user);

        verify(userRepository).findById(userId);
        verify(couponRepository).findFirstUnissuedCouponByNameWithLock(couponName);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 실패 - 발급 가능한 쿠폰 없음")
    void issueCouponByName_NoCouponAvailable() throws Exception {
        // given
        Long userId = 1L;
        String couponName = "품절 쿠폰";

        User user = createUser(userId, "testuser");

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(couponRepository.findFirstUnissuedCouponByNameWithLock(couponName)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.issueCouponByName(userId, couponName))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 가능한 쿠폰이 없습니다");

        verify(userRepository).findById(userId);
        verify(couponRepository).findFirstUnissuedCouponByNameWithLock(couponName);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 실패 - 존재하지 않는 사용자")
    void issueCouponByName_UserNotFound() {
        // given
        Long invalidUserId = 999L;
        String couponName = "신규가입 쿠폰";

        given(userRepository.findById(invalidUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.issueCouponByName(invalidUserId, couponName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verify(userRepository).findById(invalidUserId);
    }

    // Helper methods
    private User createUser(Long id, String username) throws Exception {
        User user = new User();
        setField(user, "id", id);
        setField(user, "username", username);
        return user;
    }

    private Coupon createCoupon(Long id, String name, int couponCnt, int couponAmount, int minOrderAmount, LocalDateTime expiredAt) throws Exception {
        Coupon coupon = new Coupon(name, couponCnt, couponAmount, minOrderAmount, expiredAt);
        setField(coupon, "id", id);
        return coupon;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
