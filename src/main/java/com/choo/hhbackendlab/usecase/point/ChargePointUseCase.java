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

    @Transactional
    public void chargePoint(Long userId, int amount) {
        PointWallet pointWallet = pointWalletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        pointWallet.charge(amount);
    }
}
