package com.choo.hhbackendlab.usecase.coupon;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 쿠폰 생성 (couponCnt만큼 미발급 쿠폰 생성)
 */
@Component
@RequiredArgsConstructor
public class CreateCouponUseCase {

    private final CouponRepository couponRepository;

    @Transactional
    public List<Long> createCoupons(String name, int couponCnt, int couponAmount, int minOrderAmount, LocalDateTime expiredAt) {
        List<Coupon> coupons = new ArrayList<>();

        // couponCnt만큼 동일한 쿠폰 생성 (미발급 상태)
        for (int i = 0; i < couponCnt; i++) {
            Coupon coupon = new Coupon(name, couponCnt, couponAmount, minOrderAmount, expiredAt);
            coupons.add(coupon);
        }

        // 일괄 저장
        List<Coupon> savedCoupons = couponRepository.saveAll(coupons);

        // 생성된 쿠폰 ID 목록 반환
        return savedCoupons.stream()
                .map(Coupon::getId)
                .toList();
    }
}
