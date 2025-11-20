package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.usecase.coupon.CreateCouponUseCase;
import com.choo.hhbackendlab.usecase.coupon.IssueCouponUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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


}
