package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.dto.requestDto.CreateCouponRequest;
import com.choo.hhbackendlab.dto.requestDto.IssueCouponByNameRequest;
import com.choo.hhbackendlab.dto.requestDto.IssueCouponRequest;
import com.choo.hhbackendlab.usecase.coupon.CreateCouponUseCase;
import com.choo.hhbackendlab.usecase.coupon.IssueCouponUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final IssueCouponUseCase issueCouponUseCase;
    private final CreateCouponUseCase createCouponUseCase;

    /**
     * 쿠폰 생성 API (couponCnt만큼 미발급 쿠폰 생성)
     * @param request 쿠폰 생성 요청 정보
     * @return 생성된 쿠폰 ID 목록
     */
    @PostMapping
    public ResponseEntity<List<Long>> createCoupons(@Valid @RequestBody CreateCouponRequest request) {
        List<Long> couponIds = createCouponUseCase.createCoupons(
                request.getName(),
                request.getCouponCnt(),
                request.getCouponAmount(),
                request.getMinOrderAmount(),
                request.getExpiredAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(couponIds);
    }

    /**
     * 쿠폰 발행 API (특정 쿠폰 ID로 발급)
     * @param request 쿠폰 발행 요청 정보
     * @return 성공 메시지
     */
    @PostMapping("/issue")
    public ResponseEntity<String> issueCoupon(@Valid @RequestBody IssueCouponRequest request) {
        issueCouponUseCase.issueCoupon(request.getUserId(), request.getCouponId());
        return ResponseEntity.status(HttpStatus.OK)
                .body("쿠폰이 발행되었습니다. 쿠폰 ID: " + request.getCouponId());
    }

    /**
     * 선착순 쿠폰 발급 API (쿠폰 이름으로 발급)
     * @param request 선착순 쿠폰 발급 요청 정보
     * @return 발급된 쿠폰 ID와 성공 메시지
     */
    @PostMapping("/issue-by-name")
    public ResponseEntity<String> issueCouponByName(@Valid @RequestBody IssueCouponByNameRequest request) {
        Long couponId = issueCouponUseCase.issueCouponByName(request.getUserId(), request.getCouponName());
        return ResponseEntity.status(HttpStatus.OK)
                .body("쿠폰이 발급되었습니다. 쿠폰 ID: " + couponId + ", 쿠폰명: " + request.getCouponName());
    }
}
