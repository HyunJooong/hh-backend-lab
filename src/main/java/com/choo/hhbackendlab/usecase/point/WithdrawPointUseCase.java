package com.choo.hhbackendlab.usecase.point;

import com.choo.hhbackendlab.entity.PointWallet;
import com.choo.hhbackendlab.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 환불
 */
@Component
@RequiredArgsConstructor
public class WithdrawPointUseCase {

    private final PointWalletRepository pointWalletRepository;

    @Transactional
    public void withdrawPoint(Long userId, int amount) {
        PointWallet pointWallet = pointWalletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        // 잔액 확인
        if (!pointWallet.hasEnoughBalance(amount)) {
            throw new IllegalStateException("잔액이 부족합니다. 현재 잔액: " + pointWallet.getBalance() + ", 요청 금액: " + amount);
        }

        pointWallet.use(amount);
    }
}
