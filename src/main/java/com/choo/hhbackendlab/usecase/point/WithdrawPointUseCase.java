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

    /**
     * 영속성 컨텍스트 -> Dirty Checking읕 통해 charge()메서드: 포인트 출금 및 사용
     * @param userId
     * @param amount
     */
    @Transactional
    public void withdrawPoint(Long userId, int amount) {
        //영속성 컨텍스트 스냅샷 저장
        PointWallet pointWallet = pointWalletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. User ID: " + userId));

        // 잔액 확인
        if (!pointWallet.hasEnoughBalance(amount)) {
            throw new IllegalStateException("잔액이 부족합니다. 현재 잔액: " + pointWallet.getBalance() + ", 요청 금액: " + amount);
        }

        //Dirty Checking 자동 Update
        pointWallet.use(amount);
    }
}
