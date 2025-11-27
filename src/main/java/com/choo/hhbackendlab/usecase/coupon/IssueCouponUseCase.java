package com.choo.hhbackendlab.usecase.coupon;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.entity.UserCoupon;
import com.choo.hhbackendlab.repository.CouponRepository;
import com.choo.hhbackendlab.repository.UserCouponRepository;
import com.choo.hhbackendlab.repository.UserRepository;
import com.choo.hhbackendlab.redis.CouponIssue;
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
    private final UserCouponRepository userCouponRepository;
    private final CouponIssue couponIssue;

    /**
     * 특정 쿠폰 ID로 1장 쿠폰 발급
     */
    @Transactional
    public Long issueCoupon(Long userId, Long couponId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. Coupon ID: " + couponId));

        //쿠폰 중복 여부 확인
        boolean alreadyIssued = userCouponRepository.existsByUserIdAndCouponId(userId, couponId);
        if(alreadyIssued) {
            throw new IllegalStateException(
                    "이미 발급받은 쿠폰입니다. 쿠폰명: " + coupon.getName());
        }

        // 재고 감소 성공 실패 여부 확인(동시성 이슈)
        int updatedCoupon = couponRepository.decreaseCouponCount(couponId);

        if (updatedCoupon == 0) {
            throw new IllegalStateException(
                    "쿠폰 발행 중 문제가 생겨 잠시후 다시 시도해주세요.");
        }

        // 쿠폰 발급 (couponCnt 감소 및 UserCoupon 생성)
        UserCoupon userCoupon = coupon.issueCoupon(user);

        // UserCoupon 저장
        UserCoupon saved = userCouponRepository.save(userCoupon);

        return saved.getId();
    }

    /**
     * 선착순 쿠폰 발급 (쿠폰 이름으로 발급)
     * Queue 기반 동시성 제어
     * 현재는 이름으로 발급하지만, 추후 카테고리를 생성해 쿠폰 코드번호로 발급할 예정..
     *
     * @param userId 사용자 ID
     * @param couponName 쿠폰 이름
     * @return Queue ID (실제 발급은 비동기로 처리됨)
     */
    @Transactional
    public Long issueCouponByName(Long userId, String couponName) {
        // Queue에 쿠폰 발급 요청 추가
        // 실제 쿠폰 발급은 CouponIssueQueueProcessor에서 비동기로 처리됨
        return couponIssue.addToQueue(userId, couponName);
    }
}
