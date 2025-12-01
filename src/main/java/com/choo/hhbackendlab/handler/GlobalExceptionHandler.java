package com.choo.hhbackendlab.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 글로벌 예외 핸들러
 * 애플리케이션 전역에서 발생하는 예외를 처리합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * IllegalArgumentException 예외 처리
     * 잘못된 인자가 전달된 경우 (예: 존재하지 않는 ID, 잘못된 입력값 등)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * IllegalStateException 예외 처리
     * 비즈니스 로직 상 허용되지 않는 상태인 경우 (예: 재고 부족, 포인트 부족, 이미 취소된 주문 등)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * RuntimeException 예외 처리
     * 기타 런타임 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("서버 오류가 발생했습니다: " + e.getMessage());
    }
}