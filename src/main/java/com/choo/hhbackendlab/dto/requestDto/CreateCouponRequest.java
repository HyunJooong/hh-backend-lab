package com.choo.hhbackendlab.dto.requestDto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CreateCouponRequest {

    @NotNull(message = "쿠폰 이름은 필수입니다.")
    private String name;

    @NotNull(message = "쿠폰 발행 수량은 필수입니다.")
    @Min(value = 1, message = "쿠폰 발행 수량은 1개 이상이어야 합니다.")
    private Integer couponCnt;

    @NotNull(message = "쿠폰 할인 금액은 필수입니다.")
    @Min(value = 1, message = "쿠폰 할인 금액은 1원 이상이어야 합니다.")
    private Integer couponAmount;

    @Min(value = 0, message = "최소 주문 금액은 0원 이상이어야 합니다.")
    private int minOrderAmount;

    @NotNull(message = "만료 일시는 필수입니다.")
    private LocalDateTime expiredAt;
}
