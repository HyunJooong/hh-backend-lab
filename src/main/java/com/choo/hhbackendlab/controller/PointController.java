package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.dto.requestDto.ChargePointRequest;
import com.choo.hhbackendlab.dto.requestDto.WithdrawPointRequest;
import com.choo.hhbackendlab.dto.responseDto.PointBalanceResponse;
import com.choo.hhbackendlab.usecase.point.ChargePointUseCase;
import com.choo.hhbackendlab.usecase.point.CheckBalanceUseCase;
import com.choo.hhbackendlab.usecase.point.WithdrawPointUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final ChargePointUseCase chargePointUseCase;
    private final CheckBalanceUseCase checkBalanceUseCase;
    private final WithdrawPointUseCase withdrawPointUseCase;

    /**
     * 포인트 충전 API
     * @param request 포인트 충전 요청 정보
     * @return 성공 메시지
     */
    @PostMapping("/charge")
    public ResponseEntity<String> chargePoint(@Valid @RequestBody ChargePointRequest request) {
        chargePointUseCase.chargePoint(request.getUserId(), request.getAmount());
        return ResponseEntity.status(HttpStatus.OK)
                .body("포인트가 충전되었습니다. 충전 금액: " + request.getAmount() + "원");
    }

    /**
     * 포인트 잔액 조회 API
     * @param userId 사용자 ID
     * @return 포인트 잔액 정보
     */
    @GetMapping("/{userId}/balance")
    public ResponseEntity<PointBalanceResponse> checkBalance(@PathVariable Long userId) {
        int balance = checkBalanceUseCase.checkBalance(userId);
        return ResponseEntity.ok(new PointBalanceResponse(userId, balance));
    }

    /**
     * 포인트 출금 API (WithdrawPointUseCase)
     * @param request 포인트 출금 요청 정보
     * @return 성공 메시지
     */
    @PostMapping("/refund")
    public ResponseEntity<String> refundPoint(@Valid @RequestBody WithdrawPointRequest request) {
        withdrawPointUseCase.withdrawPoint(request.getUserId(), request.getAmount());
        return ResponseEntity.ok("포인트가 출금되었습니다. 출금 금액: " + request.getAmount() + "원");
    }
}
