package com.choo.hhbackendlab.usecase.point;

import com.choo.hhbackendlab.entity.PointWallet;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.repository.PointWalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class WithdrawPointUseCaseTest {

    @Mock
    private PointWalletRepository pointWalletRepository;

    @InjectMocks
    private WithdrawPointUseCase withdrawPointUseCase;

    @Test
    @DisplayName("포인트 출금 성공 - 포인트가 정상적으로 차감된다")
    void withdrawPoint_Success() throws Exception {
        // given
        Long userId = 1L;
        int initialBalance = 20000;
        int withdrawAmount = 5000;

        User user = createUser(userId, "testuser");
        PointWallet pointWallet = new PointWallet(user, initialBalance);

        given(pointWalletRepository.findByUserIdWithLock(userId)).willReturn(Optional.of(pointWallet));

        // when
        withdrawPointUseCase.withdrawPoint(userId, withdrawAmount);

        // then
        assertThat(pointWallet.getBalance()).isEqualTo(15000);

        verify(pointWalletRepository).findByUserIdWithLock(userId);
    }

    @Test
    @DisplayName("포인트 출금 실패 - 존재하지 않는 사용자 ID")
    void withdrawPoint_UserNotFound() {
        // given
        Long invalidUserId = 999L;
        int withdrawAmount = 5000;

        given(pointWalletRepository.findByUserIdWithLock(invalidUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> withdrawPointUseCase.withdrawPoint(invalidUserId, withdrawAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verify(pointWalletRepository).findByUserIdWithLock(invalidUserId);
    }

    @Test
    @DisplayName("포인트 출금 실패 - 잔액 부족")
    void withdrawPoint_InsufficientBalance() throws Exception {
        // given
        Long userId = 1L;
        int initialBalance = 3000;
        int withdrawAmount = 5000;

        User user = createUser(userId, "testuser");
        PointWallet pointWallet = new PointWallet(user, initialBalance);

        given(pointWalletRepository.findByUserIdWithLock(userId)).willReturn(Optional.of(pointWallet));

        // when & then
        assertThatThrownBy(() -> withdrawPointUseCase.withdrawPoint(userId, withdrawAmount))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잔액이 부족합니다");

        verify(pointWalletRepository).findByUserIdWithLock(userId);
    }

    @Test
    @DisplayName("포인트 출금 실패 - 0 이하의 금액")
    void withdrawPoint_InvalidAmount() throws Exception {
        // given
        Long userId = 1L;
        int invalidAmount = 0;

        User user = createUser(userId, "testuser");
        PointWallet pointWallet = new PointWallet(user, 10000);

        given(pointWalletRepository.findByUserIdWithLock(userId)).willReturn(Optional.of(pointWallet));

        // when & then
        assertThatThrownBy(() -> withdrawPointUseCase.withdrawPoint(userId, invalidAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액을 확인해주세요");

        verify(pointWalletRepository).findByUserIdWithLock(userId);
    }

    // Helper methods
    private User createUser(Long id, String username) throws Exception {
        User user = new User();
        setField(user, "id", id);
        setField(user, "username", username);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
