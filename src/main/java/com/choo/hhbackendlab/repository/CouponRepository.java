package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 이름으로 쿠폰 템플릿 조회
     */
    List<Coupon> findByName(String name);

    /**
     * 발급 가능한 쿠폰 템플릿 조회 (선착순 발급용 - 락 없음)
     * 현재는 이름으로 조회를 하지만, 추후 쿠폰 카테고리 필드를 추가할 예정..
     */
    @Query("SELECT c FROM COUPON c WHERE c.name = :name AND c.couponCnt > 0 AND c.expiredAt > CURRENT_TIMESTAMP ORDER BY c.id ASC LIMIT 1")
    Optional<Coupon> findFirstAvailableCouponByName(@Param("name") String name);

    /**
     * 비관적 락으로 발급 가능한 쿠폰 템플릿 조회 (선착순 발급용)
     * 현재는 이름으로 조회를 하지만, 추후 쿠폰 카테고리 필드를 추가할 예정..
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM COUPON c WHERE c.name = :name AND c.couponCnt > 0 AND c.expiredAt > CURRENT_TIMESTAMP ORDER BY c.id ASC LIMIT 1")
    Optional<Coupon> findFirstAvailableCouponByNameWithLock(@Param("name") String name);
}
