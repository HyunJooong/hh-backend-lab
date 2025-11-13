package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    /**
     * 사용자가 보유한 쿠폰 조회
     */
    List<UserCoupon> findByUser(User user);

    /**
     * 사용자 ID로 쿠폰 조회
     */
    @Query("SELECT uc FROM USER_COUPON uc WHERE uc.user.id = :userId")
    List<UserCoupon> findByUserId(@Param("userId") Long userId);

    /**
     * 사용 가능한 쿠폰 조회 (발급되었고, 사용되지 않았으며, 만료되지 않은 쿠폰)
     */
    @Query("SELECT uc FROM USER_COUPON uc " +
           "WHERE uc.user.id = :userId " +
           "AND uc.isUsed = false " +
           "AND uc.coupon.expiredAt > CURRENT_TIMESTAMP")
    List<UserCoupon> findAvailableCouponsByUserId(@Param("userId") Long userId);

    /**
     * 특정 쿠폰의 발급 개수 조회
     */
    @Query("SELECT COUNT(uc) FROM USER_COUPON uc WHERE uc.coupon.id = :couponId")
    long countByCouponId(@Param("couponId") Long couponId);

    /**
     * 사용자가 특정 쿠폰을 이미 발급받았는지 확인
     */
    @Query("SELECT COUNT(uc) > 0 FROM USER_COUPON uc WHERE uc.user.id = :userId AND uc.coupon.id = :couponId")
    boolean existsByUserIdAndCouponId(@Param("userId") Long userId, @Param("couponId") Long couponId);
}