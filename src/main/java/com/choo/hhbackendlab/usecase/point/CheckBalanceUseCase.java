package com.choo.hhbackendlab.usecase.point;

import com.choo.hhbackendlab.entity.PointWallet;
import com.choo.hhbackendlab.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 잔액 확인
 */
@Component
@RequiredArgsConstructor
public class CheckBalanceUseCase {

    private final PointWalletRepository pointWalletRepository;

    @Transactional(readOnly = true)
    public int checkBalance(Long userId) {
        PointWallet pointWallet = pointWalletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        return pointWallet.getBalance();
    }
}
