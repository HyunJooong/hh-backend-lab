package com.choo.hhbackendlab.usecase.point;

import com.choo.hhbackendlab.entity.PointWallet;
import com.choo.hhbackendlab.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 충전
 */
@Component
@RequiredArgsConstructor
public class ChargePointUseCase {

    private final PointWalletRepository pointWalletRepository;

    /**
     * @param userId
     * @param amount
     * 영속성 컨텍스트 -> Dirty Checking읕 통해 charge()메서드: 포인트 충전
     */
    @Transactional
    public void chargePoint(Long userId, int amount) {
        //영속성 컨텍스트 스냅샷 저장
        PointWallet pointWallet = pointWalletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        //Dirty Checking 포인트 충전(자동 update)
        pointWallet.charge(amount);
    }
}
