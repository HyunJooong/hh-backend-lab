package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.usecase.coupon.CreateCouponUseCase;
import com.choo.hhbackendlab.usecase.coupon.IssueCouponUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CouponController.class)
public class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateCouponUseCase createCouponUseCase;

    @MockitoBean
    private IssueCouponUseCase issueCouponUseCase;

    @Test
    @DisplayName("쿠폰 생성 성공 - 100개 쿠폰 생성")
    void createCoupons_Success() throws Exception {
        // given
        String requestBody = """
                {
                    "name": "신규가입 쿠폰",
                    "couponCnt": 100,
                    "couponAmount": 10000,
                    "minOrderAmount": 50000,
                    "expiredAt": "2025-12-31T23:59:59"
                }
                """;

        List<Long> couponIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        given(createCouponUseCase.createCoupons(anyString(), anyInt(), anyInt(), anyInt(), any()))
                .willReturn(couponIds);

        // when & then
        mockMvc.perform(post("/api/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("쿠폰 발급 성공 - 특정 쿠폰 ID로 발급")
    void issueCoupon_Success() throws Exception {
        // given
        String requestBody = """
                {
                    "userId": 1,
                    "couponId": 100
                }
                """;

        doNothing().when(issueCouponUseCase).issueCoupon(1L, 100L);

        // when & then
        mockMvc.perform(post("/api/coupons/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("쿠폰이 발행되었습니다. 쿠폰 ID: 100"));
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 쿠폰")
    void issueCoupon_CouponNotFound() throws Exception {
        // given
        String requestBody = """
                {
                    "userId": 1,
                    "couponId": 999
                }
                """;

        doThrow(new IllegalArgumentException("쿠폰을 찾을 수 없습니다."))
                .when(issueCouponUseCase).issueCoupon(1L, 999L);

        // when & then
        mockMvc.perform(post("/api/coupons/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 성공")
    void issueCouponByName_Success() throws Exception {
        // given
        String requestBody = """
                {
                    "userId": 1,
                    "couponName": "신규가입 쿠폰"
                }
                """;

        given(issueCouponUseCase.issueCouponByName(1L, "신규가입 쿠폰")).willReturn(100L);

        // when & then
        mockMvc.perform(post("/api/coupons/issue-by-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("쿠폰이 발급되었습니다. 쿠폰 ID: 100, 쿠폰명: 신규가입 쿠폰"));
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 실패 - 발급 가능한 쿠폰 없음")
    void issueCouponByName_NoCouponAvailable() throws Exception {
        // given
        String requestBody = """
                {
                    "userId": 1,
                    "couponName": "품절 쿠폰"
                }
                """;

        doThrow(new IllegalStateException("발급 가능한 쿠폰이 없습니다."))
                .when(issueCouponUseCase).issueCouponByName(1L, "품절 쿠폰");

        // when & then
        mockMvc.perform(post("/api/coupons/issue-by-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is4xxClientError());
    }
}
