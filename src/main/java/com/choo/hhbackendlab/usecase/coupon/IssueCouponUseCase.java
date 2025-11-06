package com.choo.hhbackendlab.usecase.coupon;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발행
 */
@Component
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    /**
     * 특정 쿠폰 ID로 쿠폰 발급
     */
    @Transactional
    public void issueCoupon(Long userId, Long couponId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. Coupon ID: " + couponId));

        coupon.issue(user);
    }

    /**
     * 선착순 쿠폰 발급 (쿠폰 이름으로 발급)
     * 현재는 이름으로 발급하지만, 추후 카테고리를 생성해 쿠폰 코드번호로 발급할 예정..
     */
    @Transactional
    public Long issueCouponByName(Long userId, String couponName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        // 비관적 락으로 미발급 쿠폰 하나 조회 (선착순)
        Coupon coupon = couponRepository.findFirstUnissuedCouponByNameWithLock(couponName)
                .orElseThrow(() -> new IllegalStateException("발급 가능한 쿠폰이 없습니다. 쿠폰명: " + couponName));

        coupon.issue(user);

        return coupon.getId();
    }
}
