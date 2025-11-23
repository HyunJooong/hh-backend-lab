package com.choo.hhbackendlab.usecase.coupon;

import com.choo.hhbackendlab.repository.CouponRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CreateCouponUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CreateCouponUseCase createCouponUseCase;
}
