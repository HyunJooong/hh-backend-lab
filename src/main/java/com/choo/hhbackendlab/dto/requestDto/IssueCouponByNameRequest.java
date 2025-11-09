package com.choo.hhbackendlab.dto.requestDto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueCouponByNameRequest {

    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @NotNull(message = "쿠폰 이름은 필수입니다.")
    private String couponName;
}
