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
public class CheckBalanceUseCaseTest {

    @Mock
    private PointWalletRepository pointWalletRepository;

    @InjectMocks
    private CheckBalanceUseCase checkBalanceUseCase;

    @Test
    @DisplayName("잔액 조회 성공 - 현재 포인트 잔액을 반환한다")
    void checkBalance_Success() throws Exception {
        // given
        Long userId = 1L;
        int expectedBalance = 15000;

        User user = createUser(userId, "testuser");
        PointWallet pointWallet = new PointWallet(user, expectedBalance);

        given(pointWalletRepository.findByUserId(userId)).willReturn(Optional.of(pointWallet));

        // when
        int actualBalance = checkBalanceUseCase.checkBalance(userId);

        // then
        assertThat(actualBalance).isEqualTo(expectedBalance);

        verify(pointWalletRepository).findByUserId(userId);
    }

   /* @Test
    @DisplayName("잔액 조회 실패 - 존재하지 않는 사용자 ID")
    void checkBalance_UserNotFound() {
        // given
        Long invalidUserId = 999L;

        given(pointWalletRepository.findByUserId(invalidUserId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> checkBalanceUseCase.checkBalance(invalidUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("포인트 지갑을 찾을 수 없습니다");

        verify(pointWalletRepository).findByUserId(invalidUserId);
    }*/

    @Test
    @DisplayName("잔액 조회 성공 - 잔액이 0인 경우")
    void checkBalance_ZeroBalance() throws Exception {
        // given
        Long userId = 1L;
        int zeroBalance = 0;

        User user = createUser(userId, "testuser");
        PointWallet pointWallet = new PointWallet(user, zeroBalance);

        given(pointWalletRepository.findByUserId(userId)).willReturn(Optional.of(pointWallet));

        // when
        int actualBalance = checkBalanceUseCase.checkBalance(userId);

        // then
        assertThat(actualBalance).isEqualTo(0);

        verify(pointWalletRepository).findByUserId(userId);
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
