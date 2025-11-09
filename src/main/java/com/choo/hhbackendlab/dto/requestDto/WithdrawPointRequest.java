package com.choo.hhbackendlab.dto.requestDto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WithdrawPointRequest {

    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @NotNull(message = "출금 금액은 필수입니다.")
    @Min(value = 1, message = "출금 금액은 1원 이상이어야 합니다.")
    private Integer amount;
}
