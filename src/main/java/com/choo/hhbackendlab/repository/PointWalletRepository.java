package com.choo.hhbackendlab.repository;

import com.choo.hhbackendlab.entity.PointWallet;
import com.choo.hhbackendlab.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {

    /**
     * User로 PointWallet 조회
     */
    Optional<PointWallet> findByUser(User user);

    /**
     * User ID로 PointWallet 조회
     */
    @Query("SELECT pw FROM POINT_WALLET pw WHERE pw.user.id = :userId")
    Optional<PointWallet> findByUserId(@Param("userId") Long userId);

    /**
     * 비관적 락으로 PointWallet 조회 (동시성 제어)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pw FROM POINT_WALLET pw WHERE pw.user.id = :userId")
    Optional<PointWallet> findByUserIdWithLock(@Param("userId") Long userId);

    /**
     * 포인트 원자적 감소
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE POINT_WALLET pw SET pw.balance = pw.balance - :amount " +
            "WHERE pw.user.id = :userId AND pw.balance >= :amount")
    int decreaseBalance(@Param("userId") Long userId,
                        @Param("amount") int amount);
}