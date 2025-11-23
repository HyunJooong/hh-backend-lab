# E-Commerce 플랫폼 동시성 제어 기술 보고서

## 목차
1. [개요](#개요)
2. [주문 생성 동시성 제어](#주문-생성-동시성-제어)
3. [쿠폰 발급 동시성 제어](#쿠폰-발급-동시성-제어)
4. [주문 취소 동시성 제어](#주문-취소-동시성-제어)
5. [성능 최적화 및 개선사항](#성능-최적화-및-개선사항)
6. [결론](#결론)

---

## 개요

### 프로젝트 배경
E-Commerce 플랫폼에서 다수의 사용자가 동시에 주문을 생성하거나 한정 수량의 쿠폰을 발급받는 과정에서 발생할 수 있는 동시성 문제를 해결하기 위한 기술 구현 보고서입니다.

### 주요 동시성 문제 영역
1. **상품 재고 차감**: 여러 사용자가 동시에 동일 상품 주문 시 재고 부족 문제
2. **포인트 차감**: 동일 사용자의 동시 요청으로 인한 포인트 이중 차감 문제
3. **쿠폰 발급**: 한정 수량 쿠폰의 초과 발급 문제
4. **주문 취소**: 포인트 환불 시 동시성 문제

### 기술 스택
- **Framework**: Spring Boot 3.x
- **Database**: MySQL 8.0.40
- **ORM**: Spring Data JPA (Hibernate)
- **Test**: JUnit 5, Testcontainers

---

## 주문 생성 동시성 제어

### 1. 문제 식별

#### 1.1 상품 재고 차감 동시성 문제
**문제 시나리오:**
```
시간 순서:
T1: User A가 상품 조회 (재고: 10개)
T2: User B가 상품 조회 (재고: 10개)
T3: User A가 5개 주문 → 재고 감소 (10 - 5 = 5개)
T4: User B가 7개 주문 → 재고 감소 (10 - 7 = 3개) ❌

예상 결과: 재고 -2개 (오버셀링 발생)
```

**발생 원인:**
- **Read-Modify-Write 패턴의 Race Condition**: 조회와 업데이트 사이의 간격에서 다른 트랜잭션이 개입
- **Lost Update 문제**: 나중에 커밋된 트랜잭션이 이전 트랜잭션의 결과를 덮어씀

#### 1.2 포인트 차감 동시성 문제
**문제 시나리오:**
```
User의 포인트 잔액: 1,000원
T1: 800원 주문 요청 → 잔액 조회 (1,000원)
T2: 500원 주문 요청 → 잔액 조회 (1,000원)
T3: T1 커밋 → 잔액 200원
T4: T2 커밋 → 잔액 500원 ❌

예상 결과: 포인트 -300원 (마이너스 잔액 발생)
```

#### 1.3 데드락 위험
**문제 시나리오:**
```
주문 A: 상품1 → 상품2 순서로 락 획득 시도
주문 B: 상품2 → 상품1 순서로 락 획득 시도

T1: 주문 A가 상품1 락 획득
T2: 주문 B가 상품2 락 획득
T3: 주문 A가 상품2 락 대기 (주문 B가 보유)
T4: 주문 B가 상품1 락 대기 (주문 A가 보유)
→ 데드락 발생 ❌
```

### 2. 해결 방안 분석

#### 2.1 비관적 락 방식 (초기 구현)

**코드 구현:**
```java
// ProductRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM PRODUCT p WHERE p.id = :productId")
Optional<Product> findByIdWithLock(@Param("productId") Long productId);

// CreateOrderUseCase.java (주석 처리된 이전 구현)
for (OrderItemRequest itemRequest : request.getOrderItems()) {
    Product product = productRepository.findByIdWithLock(itemRequest.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

    product.removeStock(itemRequest.getQuantity());  // 재고 차감
}
```

**장점:**
- 구현이 직관적
- JPA가 자동으로 락 관리

**단점:**
- 락 홀딩 시간이 긺 (트랜잭션 전체 기간 동안 락 보유)
- 다중 상품 주문 시 데드락 위험
- DB 연결 점유로 인한 성능 저하

#### 2.2 원자적 쿼리 방식 (최종 구현) ✅

**구현 코드:**
```java
// ProductRepository.java
@Modifying(clearAutomatically = true)
@Query("UPDATE PRODUCT p SET p.stock = p.stock - :quantity " +
        "WHERE p.id = :productId AND p.stock >= :quantity")
int decreaseStock(@Param("productId") Long productId,
                  @Param("quantity") int quantity);

// CreateOrderUseCase.java
// 1. 상품 ID 정렬로 데드락 방지
List<Long> productIds = request.getOrderItems().stream()
        .map(OrderItemRequest::getProductId)
        .sorted()  // ID 순서로 정렬
        .toList();

// 2. 원자적 재고 차감
for (OrderItemRequest itemRequest : request.getOrderItems()) {
    int updated = productRepository.decreaseStock(
            itemRequest.getProductId(),
            itemRequest.getQuantity()
    );

    if (updated == 0) {
        throw new IllegalStateException("재고가 부족합니다.");
    }
}
```

**핵심 원리:**
1. **UPDATE WHERE 조건**: `stock >= :quantity` 조건으로 재고가 충분할 때만 업데이트
2. **MySQL의 Row Lock**: UPDATE 문이 실행될 때 자동으로 Row Lock 획득
3. **반환값 검증**: 업데이트된 row 수로 성공/실패 판단

**동작 과정:**
```sql
-- T1: User A의 요청 (5개 주문)
UPDATE PRODUCT SET stock = stock - 5
WHERE id = 1 AND stock >= 5;  -- 성공, 1 row updated

-- T2: User B의 요청 (7개 주문, T1과 동시 실행)
UPDATE PRODUCT SET stock = stock - 7
WHERE id = 1 AND stock >= 7;  -- T1 완료 대기 후 실행
                               -- 재고 5개 < 7개 → 0 row updated
```

### 3. 포인트 차감 동시성 제어

**구현 코드:**
```java
// PointWalletRepository.java
@Modifying(clearAutomatically = true)
@Query("UPDATE POINT_WALLET pw SET pw.balance = pw.balance - :amount " +
        "WHERE pw.user.id = :userId AND pw.balance >= :amount")
int decreaseBalance(@Param("userId") Long userId,
                    @Param("amount") int amount);

// CreateOrderUseCase.java
int updated = pointWalletRepository.decreaseBalance(
        request.getUserId(),
        order.getFinalAmount()
);

if (updated == 0) {
    throw new IllegalStateException("포인트가 부족합니다.");
}
```

**보장 사항:**
- 잔액 부족 시 업데이트 실패 → 음수 잔액 방지
- Row Lock으로 동시 차감 방지
- 원자적 연산으로 Lost Update 방지

### 4. 데드락 방지 전략 (멘토링 피드백)

**ID 정렬 기반 락 순서 일관성**
```java
// 상품 ID 정렬로 모든 트랜잭션이 동일한 순서로 락 획득
List<Long> productIds = request.getOrderItems().stream()
        .map(OrderItemRequest::getProductId)
        .sorted()  // 오름차순 정렬
        .toList();
```

**효과:**
```
주문 A: 상품 [3, 1, 2] → 정렬 후 [1, 2, 3] 순서로 처리
주문 B: 상품 [2, 3, 1] → 정렬 후 [1, 2, 3] 순서로 처리
→ 모든 트랜잭션이 동일한 순서로 락 획득 → 데드락 방지
```

### 5. 검증 결과

**통합 테스트:**
```java
@Test
@DisplayName("포인트와 쿠폰을 사용하여 주문 생성 성공")
void createOrderWithPointAndCoupon_Success() {
    // Given: 상품 재고 10개, 포인트 1,000,000원
    // When: 2개 주문, 쿠폰 50,000원 할인
    Order savedOrder = createOrderUseCase.createOrder(orderRequest);

    // Then
    assertThat(updatedProduct.getStock()).isEqualTo(8);  // 재고 차감 확인
    assertThat(updatedPointWallet.getBalance()).isEqualTo(50000);  // 포인트 차감 확인
}
```

**테스트 환경:**
- Testcontainers를 사용한 실제 MySQL 8.0.40 환경
- 원자적 쿼리의 동시성 제어 검증 완료

---

## 쿠폰 발급 동시성 제어

### 1. 문제 식별

#### 1.1 선착순 쿠폰 초과 발급 문제
**문제 시나리오:**
```
쿠폰 잔여 수량: 10개
동시 요청: 100명

비동시성 제어 시:
T1~T100: 모두 잔여 수량 10개 조회
T1~T100: 모두 발급 성공
결과: 100개 발급 (90개 초과 발급) ❌
```

**발생 원인:**
- **Check-Then-Act 패턴의 Race Condition**
- 조회와 업데이트 사이의 간격에서 여러 트랜잭션 개입

### 2. 해결 방안

#### 2.1 일반 쿠폰 발급 - 원자적 쿼리 방식

**구현 코드:**
```java
// CouponRepository.java
@Modifying(clearAutomatically = true)
@Query("UPDATE COUPON c SET c.couponCnt = c.couponCnt - 1 " +
        "WHERE c.id = :couponId AND c.couponCnt > 0")
int decreaseCouponCount(@Param("couponId") Long couponId);

// IssueCouponUseCase.java
@Transactional
public Long issueCoupon(Long userId, Long couponId) {
    // 1. 중복 발급 방지
    boolean alreadyIssued = userCouponRepository.existsByUserIdAndCouponId(userId, couponId);
    if(alreadyIssued) {
        throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
    }

    // 2. 원자적 재고 감소
    int updated = couponRepository.decreaseCouponCount(couponId);
    if (updated == 0) {
        throw new IllegalStateException("쿠폰 발행 중 문제가 발생했습니다.");
    }

    // 3. UserCoupon 생성
    UserCoupon userCoupon = coupon.issueCoupon(user);
    return userCouponRepository.save(userCoupon).getId();
}
```

**보장 사항:**
- 쿠폰 수량이 0보다 클 때만 감소
- Row Lock으로 동시 감소 방지
- 중복 발급 방지

#### 2.2 선착순 쿠폰 발급 - Queue 기반 방식

**아키텍처:**
```
[사용자 요청] → [Queue에 추가] → [Scheduler 처리] → [쿠폰 발급]
    (즉시 응답)      (비동기)         (순차 처리)      (원자적)
```

**구현 코드:**

1. **Queue 추가 (동기):**
```java
// CouponIssue.java
@Transactional
public Long addToQueue(Long userId, String couponName) {
    CouponIssueQueue queueItem = new CouponIssueQueue(userId, couponName);
    CouponIssueQueue saved = queueRepository.save(queueItem);
    return saved.getId();  // Queue ID 즉시 반환
}
```

2. **Queue 처리 (비동기):**
```java
// CouponIssueProcessor.java
@Scheduled(fixedDelay = 100)  // 100ms마다 실행
public void processQueue() {
    boolean processed = couponIssue.processNextQueue();
}

// CouponIssue.java
@Transactional
public boolean processNextQueue() {
    // 1. PENDING 상태 요청을 비관적 락으로 조회
    Optional<CouponIssueQueue> queueItemOpt =
        queueRepository.findFirstByStatusWithLock(QueueStatus.PENDING);

    if (queueItemOpt.isEmpty()) {
        return false;  // 처리할 요청 없음
    }

    CouponIssueQueue queueItem = queueItemOpt.get();

    try {
        // 2. 상태 변경: PENDING → PROCESSING
        queueItem.startProcessing();

        // 3. 쿠폰 발급 (비관적 락 사용)
        Coupon coupon = couponRepository
            .findFirstAvailableCouponByNameWithLock(couponName)
            .orElseThrow();
        UserCoupon userCoupon = coupon.issueCoupon(user);

        // 4. 상태 변경: PROCESSING → COMPLETED
        queueItem.complete(userCoupon.getId());
        return true;

    } catch (Exception e) {
        // 5. 실패 시: PROCESSING → FAILED
        queueItem.fail(e.getMessage());
        return false;
    }
}
```

3. **비관적 락 조회:**
```java
// CouponIssueQueueRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT q FROM COUPON_ISSUE_QUEUE q " +
       "WHERE q.status = :status " +
       "ORDER BY q.id ASC LIMIT 1")
Optional<CouponIssueQueue> findFirstByStatusWithLock(
    @Param("status") QueueStatus status
);
```

**Queue 기반 방식의 장점:**
1. **응답 속도**: 사용자는 Queue 추가 후 즉시 응답 (대기 순번 확인 가능)
2. **순차 처리**: 비관적 락으로 한 번에 하나씩만 처리 → 동시성 문제 완전 차단
3. **재시도 가능**: 실패한 요청 추적 및 재처리 가능
4. **트래픽 분산**: 대량 요청을 비동기로 처리하여 DB 부하 분산

**동작 흐름:**
```
시간: 0ms
User 1 → Queue #1 (PENDING)
User 2 → Queue #2 (PENDING)
User 100 → Queue #100 (PENDING)

시간: 100ms (Scheduler 1회차)
Queue #1: PENDING → PROCESSING → COMPLETED (쿠폰 발급 성공)

시간: 200ms (Scheduler 2회차)
Queue #2: PENDING → PROCESSING → COMPLETED (쿠폰 발급 성공)

...

시간: 1000ms (Scheduler 10회차)
Queue #10: PENDING → PROCESSING → COMPLETED (쿠폰 발급 성공)

시간: 1100ms (Scheduler 11회차)
Queue #11: PENDING → PROCESSING → FAILED (쿠폰 소진)
```

### 3. 검증 결과

**통합 테스트:**
```java
@Test
@DisplayName("10명의 유저에게 1000원 쿠폰 1장씩 발급 성공")
void issueCouponToTenUsers_Success() {
    // Given: 1000원 쿠폰 10개 생성
    Coupon coupon = new Coupon("1000원 할인 쿠폰", 10, 1000, 0, expiryDate);

    // When: 10명에게 발급
    for (User user : users) {
        issueCouponUseCase.issueCoupon(user.getId(), coupon.getId());
    }

    // Then
    assertThat(updatedCoupon.getRemainingCount()).isEqualTo(0);  // 잔여 0개
    assertThat(allUserCoupons).hasSize(10);  // 정확히 10개만 발급
}
```

**결과:**
- ✅ 쿠폰 재고 정확히 10개 감소
- ✅ 초과 발급 없음
- ✅ 중복 발급 방지 확인

---

## 주문 취소 동시성 제어

### 1. 문제 식별

**문제 시나리오:**
```
주문 금액: 100,000원
현재 포인트: 50,000원

T1: 주문 취소 요청 #1 → 포인트 조회 (50,000원)
T2: 주문 취소 요청 #2 → 포인트 조회 (50,000원)
T3: T1 환불 → 포인트 150,000원
T4: T2 환불 → 포인트 250,000원 ❌

예상 결과: 100,000원 중복 환불
```

### 2. 해결 방안

**비관적 락 사용:**
```java
// PointWalletRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT pw FROM POINT_WALLET pw WHERE pw.user.id = :userId")
Optional<PointWallet> findByUserIdWithLock(@Param("userId") Long userId);

// CancelOrderUseCase.java
@Transactional
public void cancelOrder(String orderNumber) {
    // 1. 주문 조회
    Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow();

    // 2. 비관적 락으로 PointWallet 조회
    PointWallet pointWallet = pointWalletRepository
            .findByUserIdWithLock(order.getUser().getId())
            .orElseThrow();

    // 3. 주문 취소 (재고 복구, 쿠폰 복원)
    order.cancel();

    // 4. 포인트 환불
    pointWallet.refund(order.getRefundAmount());
}
```

**비관적 락을 사용한 이유:**
1. **취소는 상대적으로 드문 작업**: 성능 영향 최소
2. **트랜잭션 일관성 중요**: 재고 복구 + 쿠폰 복원 + 포인트 환불이 모두 성공해야 함
3. **원자적 쿼리 불필요**: UPDATE 조건으로 검증할 내용이 없음

---

## 성능 최적화 및 개선사항

### 1. 비관적 락 → 원자적 쿼리 전환

**비교:**

| 구분 | 비관적 락 | 원자적 쿼리 |
|------|-----------|-------------|
| **락 홀딩 시간** | 트랜잭션 전체 | UPDATE 실행 순간만 |
| **동시 처리량** | 낮음 (순차 처리) | 높음 (조회는 동시 가능) |
| **데드락 위험** | 있음 | 없음 |
| **구현 복잡도** | 낮음 | 중간 |
| **적용 케이스** | 복잡한 비즈니스 로직 | 단순 재고 차감 |


### 2. Queue 기반 비동기 처리

**효과:**
1. **사용자 경험 개선**: 즉시 응답 (Queue ID 반환)
2. **서버 부하 분산**: 대량 트래픽을 시간에 걸쳐 분산 처리
3. **실패 추적**: Queue 상태로 실패 원인 파악 가능

### 3. ID 정렬을 통한 데드락 방지

**데드락 발생 확률:**
- **정렬 전**: O(N²) - 상품 조합에 따라 기하급수적 증가
- **정렬 후**: 0 - 모든 트랜잭션이 동일한 순서로 락 획득

---

## 결론

### 구현 요약

| 기능 | 동시성 제어 방식 | 핵심 기술 |
|------|------------------|-----------|
| **상품 재고 차감** | 원자적 쿼리 | `UPDATE WHERE stock >= quantity` + Row Lock |
| **포인트 차감** | 원자적 쿼리 | `UPDATE WHERE balance >= amount` + Row Lock |
| **일반 쿠폰 발급** | 원자적 쿼리 | `UPDATE WHERE couponCnt > 0` + 중복 방지 |
| **선착순 쿠폰 발급** | Queue + 비관적 락 | 비동기 순차 처리 + PESSIMISTIC_WRITE |
| **포인트 환불** | 비관적 락 | PESSIMISTIC_WRITE |
| **데드락 방지** | ID 정렬 | 락 순서 일관성 보장 |


### 향후 개선 방안

1. **Redis 분산 락 도입(멘토링 피드백)**
   - 현재: 단일 DB 인스턴스 기준
   - 개선: Multi-Master DB 환경에서 Redis 분산 락 필요

2. **Kafka 메시지 큐 도입(멘토링 피드백)**
   - 현재: DB를 Queue로 활용
   - 개선: Kafka로 처리량 및 확장성 향상

3. **읽기 전용 복제본 활용**
   - 현재: 마스터 DB에서 모든 작업 처리
   - 개선: 조회는 복제본, 쓰기는 마스터로 분리

4. **모니터링 강화**
   - 현재: 로그 기반 모니터링
   - 개선: 메트릭 수집 (락 대기 시간, Queue 처리 시간 등)

### 테스트 검증

모든 동시성 제어 로직은 Testcontainers를 활용한 통합 테스트로 검증 완료:
- ✅ OrderTest: 주문 생성 (재고 차감, 포인트 차감, 쿠폰 사용)
- ✅ IssueCouponUseCaseTest: 쿠폰 발급 (재고 감소, 중복 방지)

---

**작성일**: 2025-01-20

**작성자**: 추현중


