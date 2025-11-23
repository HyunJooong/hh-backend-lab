package com.choo.hhbackendlab.usecase.coupon;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 쿠폰 생성 (couponCnt만큼 미발급 쿠폰 생성)
 */
@Component
@RequiredArgsConstructor
public class CreateCouponUseCase {

    private final CouponRepository couponRepository;

    @Transactional
    public Coupon createCoupons(String name, int couponCnt, int couponAmount, int minOrderAmount, LocalDateTime expiredAt) {
        Coupon coupons = new Coupon(name, couponCnt, couponAmount, minOrderAmount, expiredAt);

        Coupon couponResult = couponRepository.save(coupons);

        // 생성된 쿠폰 반환
        return couponResult;
    }
}
