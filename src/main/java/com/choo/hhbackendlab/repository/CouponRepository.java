package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 사용자가 보유한 쿠폰 조회
     */
    List<Coupon> findByUser(User user);

    /**
     * 사용자 ID로 쿠폰 조회
     */
    @Query("SELECT c FROM COUPON c WHERE c.user.id = :userId")
    List<Coupon> findByUserId(@Param("userId") Long userId);

    /**
     * 미발급 쿠폰 중 특정 이름의 쿠폰 조회 (user가 null인 쿠폰)
     */
    @Query("SELECT c FROM COUPON c WHERE c.user IS NULL AND c.name = :name")
    List<Coupon> findUnissuedCouponsByName(@Param("name") String name);

    /**
     * 비관적 락으로 미발급 쿠폰 하나 조회 (선착순 발급용)
     * 현재는 이름으로 조회를 하지만, 추후 쿠폰 카테고리 필드를 추가할 예정..
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM COUPON c WHERE c.user IS NULL AND c.name = :name AND c.expiredAt > CURRENT_TIMESTAMP ORDER BY c.id ASC")
    Optional<Coupon> findFirstUnissuedCouponByNameWithLock(@Param("name") String name);

    /**
     * 사용 가능한 쿠폰 조회 (발급되었고, 사용되지 않았으며, 만료되지 않은 쿠폰)
     */
    @Query("SELECT c FROM COUPON c WHERE c.user.id = :userId AND c.isUsed = false AND c.expiredAt > CURRENT_TIMESTAMP")
    List<Coupon> findAvailableCouponsByUserId(@Param("userId") Long userId);
}
