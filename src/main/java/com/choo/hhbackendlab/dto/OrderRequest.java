package com.choo.hhbackendlab.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderRequest {

    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다.")
    @Valid
    private List<OrderItemRequest> orderItems;

    @NotNull(message = "금액을 확인해주세요.")
    @Min(value = 0, message = "최종 금액은 0원 이상이어야 합니다")
    private int amount;
}
