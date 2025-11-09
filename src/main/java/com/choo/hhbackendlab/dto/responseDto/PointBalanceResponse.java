package com.choo.hhbackendlab.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PointBalanceResponse {
    private Long userId;
    private int balance;
}
