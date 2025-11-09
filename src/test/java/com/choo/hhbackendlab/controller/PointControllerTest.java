package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.usecase.point.ChargePointUseCase;
import com.choo.hhbackendlab.usecase.point.CheckBalanceUseCase;
import com.choo.hhbackendlab.usecase.point.WithdrawPointUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
public class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChargePointUseCase chargePointUseCase;

    @MockitoBean
    private CheckBalanceUseCase checkBalanceUseCase;

    @MockitoBean
    private WithdrawPointUseCase withdrawPointUseCase;

    @Test
    @DisplayName("포인트 충전 성공")
    void chargePoint_Success() throws Exception {
        // given
        String requestBody = """
                {
                    "userId": 1,
                    "amount": 10000
                }
                """;

        doNothing().when(chargePointUseCase).chargePoint(1L, 10000);

        // when & then
        mockMvc.perform(post("/api/points/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("포인트가 충전되었습니다. 충전 금액: 10000원"));
    }

    @Test
    @DisplayName("포인트 잔액 조회 성공")
    void checkBalance_Success() throws Exception {
        // given
        Long userId = 1L;
        int balance = 15000;

        given(checkBalanceUseCase.checkBalance(userId)).willReturn(balance);

        // when & then
        mockMvc.perform(get("/api/points/{userId}/balance", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.balance").value(15000));
    }

    @Test
    @DisplayName("포인트 출금 성공")
    void withdrawPoint_Success() throws Exception {
        // given
        String requestBody = """
                {
                    "userId": 1,
                    "amount": 5000
                }
                """;

        doNothing().when(withdrawPointUseCase).withdrawPoint(1L, 5000);

        // when & then
        mockMvc.perform(post("/api/points/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("포인트가 출금되었습니다. 출금 금액: 5000원"));
    }

    @Test
    @DisplayName("포인트 출금 실패 - 잔액 부족")
    void withdrawPoint_InsufficientBalance() throws Exception {
        // given
        String requestBody = """
                {
                    "userId": 1,
                    "amount": 100000
                }
                """;

        doThrow(new IllegalStateException("잔액이 부족합니다."))
                .when(withdrawPointUseCase).withdrawPoint(1L, 100000);

        // when & then
        mockMvc.perform(post("/api/points/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is4xxClientError());
    }
}
