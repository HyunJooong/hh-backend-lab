package com.choo.hhbackendlab.usecase.coupon;

import com.choo.hhbackendlab.entity.Coupon;
import com.choo.hhbackendlab.repository.CouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class CreateCouponUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CreateCouponUseCase createCouponUseCase;

    @Test
    @DisplayName("쿠폰 생성 성공 - 지정된 개수만큼 쿠폰이 생성된다")
    void createCoupons_Success() throws Exception {
        // given
        String couponName = "신규가입 쿠폰";
        int couponCnt = 100;
        int couponAmount = 10000;
        int minOrderAmount = 50000;
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);

        // Mock 저장된 쿠폰 목록 생성
        List<Coupon> savedCoupons = new ArrayList<>();
        for (int i = 1; i <= couponCnt; i++) {
            Coupon coupon = new Coupon(couponName, couponCnt, couponAmount, minOrderAmount, expiredAt);
            setField(coupon, "id", (long) i);
            savedCoupons.add(coupon);
        }

        given(couponRepository.saveAll(anyList())).willReturn(savedCoupons);

        // when
        List<Long> couponIds = createCouponUseCase.createCoupons(couponName, couponCnt, couponAmount, minOrderAmount, expiredAt);

        // then
        assertThat(couponIds).hasSize(100);
        assertThat(couponIds.get(0)).isEqualTo(1L);
        assertThat(couponIds.get(99)).isEqualTo(100L);

        // 저장 메서드 호출 검증
        ArgumentCaptor<List<Coupon>> captor = ArgumentCaptor.forClass(List.class);
        verify(couponRepository).saveAll(captor.capture());

        List<Coupon> capturedCoupons = captor.getValue();
        assertThat(capturedCoupons).hasSize(100);
        assertThat(capturedCoupons.get(0).getName()).isEqualTo(couponName);
        assertThat(capturedCoupons.get(0).getCouponAmount()).isEqualTo(couponAmount);
    }

    @Test
    @DisplayName("쿠폰 생성 성공 - 1개의 쿠폰 생성")
    void createCoupons_SingleCoupon() throws Exception {
        // given
        String couponName = "VIP 쿠폰";
        int couponCnt = 1;
        int couponAmount = 50000;
        int minOrderAmount = 100000;
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(7);

        List<Coupon> savedCoupons = new ArrayList<>();
        Coupon coupon = new Coupon(couponName, couponCnt, couponAmount, minOrderAmount, expiredAt);
        setField(coupon, "id", 1L);
        savedCoupons.add(coupon);

        given(couponRepository.saveAll(anyList())).willReturn(savedCoupons);

        // when
        List<Long> couponIds = createCouponUseCase.createCoupons(couponName, couponCnt, couponAmount, minOrderAmount, expiredAt);

        // then
        assertThat(couponIds).hasSize(1);
        assertThat(couponIds.get(0)).isEqualTo(1L);

        verify(couponRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("쿠폰 생성 성공 - 최소 주문 금액이 0인 쿠폰")
    void createCoupons_NoMinimumOrder() throws Exception {
        // given
        String couponName = "무제한 쿠폰";
        int couponCnt = 10;
        int couponAmount = 5000;
        int minOrderAmount = 0; // 최소 주문 금액 제한 없음
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);

        List<Coupon> savedCoupons = new ArrayList<>();
        for (int i = 1; i <= couponCnt; i++) {
            Coupon coupon = new Coupon(couponName, couponCnt, couponAmount, minOrderAmount, expiredAt);
            setField(coupon, "id", (long) i);
            savedCoupons.add(coupon);
        }

        given(couponRepository.saveAll(anyList())).willReturn(savedCoupons);

        // when
        List<Long> couponIds = createCouponUseCase.createCoupons(couponName, couponCnt, couponAmount, minOrderAmount, expiredAt);

        // then
        assertThat(couponIds).hasSize(10);

        ArgumentCaptor<List<Coupon>> captor = ArgumentCaptor.forClass(List.class);
        verify(couponRepository).saveAll(captor.capture());

        List<Coupon> capturedCoupons = captor.getValue();
        assertThat(capturedCoupons.get(0).getMinOrderAmount()).isEqualTo(0);
    }

    // Helper methods
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
