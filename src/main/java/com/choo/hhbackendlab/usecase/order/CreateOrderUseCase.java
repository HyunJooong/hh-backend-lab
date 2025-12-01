package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.dto.requestDto.OrderItemRequest;
import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.helper.DistributedLockHelper;
import com.choo.hhbackendlab.helper.OrderTransactionProcessor;
import com.choo.hhbackendlab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 새로운 주문을 생성하고, 포인트 결제를 처리하며, 재고를 차감한다
 */
@Component
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final UserRepository userRepository;
    private final DistributedLockHelper lockHelper;
    private final OrderTransactionProcessor transactionProcessor;

    public Order createOrder(OrderRequest request) {

        // 1. 기본 검증 및 사용자 조회
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "사용자를 찾을 수 없습니다. ID: " + request.getUserId()));

        // 주문 아이템 검증
        if (request.getOrderItems() == null || request.getOrderItems().isEmpty()) {
            throw new IllegalArgumentException("주문 상품이 없습니다.");
        }

        // 2. 상품별 분산락 적용 (overselling 방지)
        // 주문에 포함된 모든 상품 ID를 정렬하여 데드락 방지
        List<Long> productIds = request.getOrderItems().stream()
                .map(OrderItemRequest::getProductId)
                .sorted()
                .toList();

        // 각 상품에 대해 순차적으로 락을 획득하며 주문 처리
        return processOrderWithProductLocks(request, user, productIds, 0);
    }

    /**
     * 상품별 락을 재귀적으로 획득하며 주문을 처리합니다.
     *
     * @param request 주문 요청
     * @param user 주문 사용자
     * @param productIds 정렬된 상품 ID 리스트
     * @param index 현재 처리 중인 상품 인덱스
     * @return 생성된 주문
     */
    private Order processOrderWithProductLocks(
            OrderRequest request,
            User user,
            List<Long> productIds,
            int index) {

        // 모든 상품에 대한 락을 획득한 경우, 실제 주문 처리
        if (index >= productIds.size()) {
            return transactionProcessor.processOrder(request, user);
        }

        // 현재 상품에 대한 락 획득
        String lockKey = "order:product:" + productIds.get(index);

        return lockHelper.executeWithLock(lockKey, 5, 10, () -> {
            // 다음 상품에 대한 락 획득 (재귀)
            return processOrderWithProductLocks(request, user, productIds, index + 1);
        });
    }
}

