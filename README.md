# 이커머스 백엔드 시스템

Spring Boot 기반의 이커머스 백엔드 API 서버입니다. 상품 관리, 주문, 결제, 포인트, 쿠폰 등의 핵심 기능을 제공합니다.

## 📋 목차

- [프로젝트 소개](#프로젝트-소개)
- [기술 스택](#기술-스택)
- [주요 기능](#주요-기능)
- [API 명세](#api-명세)
- [아키텍처](#아키텍처)
- [ERD](#erd)
- [동시성 제어](#동시성-제어)
- [설치 및 실행](#설치-및-실행)
- [테스트](#테스트)
- [성능 최적화](#성능-최적화)

## 🎯 프로젝트 소개

이 프로젝트는 실제 이커머스 서비스에서 필요한 핵심 기능들을 구현한 백엔드 시스템입니다.

### 핵심 도메인

- **상품 관리**: 상품 등록, 재고 관리, 조회수 추적
- **주문 시스템**: 주문 생성, 조회, 취소
- **포인트 시스템**: 포인트 충전, 사용, 출금, 잔액 조회
- **쿠폰 시스템**: 쿠폰 생성, 선착순 발급, 사용
- **인기 상품**: 조회수 및 판매량 기반 순위 계산

## 🛠 기술 스택

### Backend
- **Java 17**
- **Spring Boot 3.x**
- **Spring Data JPA**
- **Hibernate**

### Database
- **mysql** 

### Test
- **JUnit 5**
- **Mockito**
- **AssertJ**
- **Spring Boot Test**

### Build Tool
- **Gradle** 

## ✨ 주요 기능

### 1. 상품 관리
- ✅ 상품 등록 및 조회
- ✅ 재고 관리 (적재/차감)
- ✅ 조회수 추적
- ✅ 조회수 기반 인기 상품 순위
- ✅ 최근 7일 판매량 기반 인기 상품 순위

### 2. 주문 시스템
- ✅ 주문 생성 (여러 상품 한 번에 주문)
- ✅ 주문 조회 (주문번호 기반)
- ✅ 주문 취소
- ✅ 쿠폰 할인 적용
- ✅ 포인트 결제

### 3. 포인트 시스템
- ✅ 포인트 충전
- ✅ 포인트 사용 (상품 구매)
- ✅ 포인트 출금
- ✅ 잔액 조회
- ✅ 낙관적 락을 통한 동시성 제어

### 4. 쿠폰 시스템
- ✅ 쿠폰 템플릿 생성 (couponCnt 기반 재고 관리)
- ✅ 특정 쿠폰 ID로 발급
- ✅ **Queue 기반 선착순 쿠폰 발급** (비동기 처리)
- ✅ 쿠폰 발급 상태 추적 (PENDING → PROCESSING → COMPLETED/FAILED)
- ✅ 비관적 락을 통한 동시성 제어
- ✅ 쿠폰 사용 및 만료 관리
- ✅ 사용자별 발급 이력 관리 (UserCoupon)

### 5. Queue 처리 시스템
- ✅ 스케줄러 기반 비동기 Queue 처리 (100ms 간격)
- ✅ 쿠폰 발급 요청 순차 처리
- ✅ 실패 처리 및 재시도 가능
- ✅ 처리 상태 모니터링

## 📡 API 명세

### 상품 (Product)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/products` | 상품 등록 |
| GET | `/api/products/{productId}/stock` | 재고 조회 |
| PATCH | `/api/products/{productId}/stock/add` | 재고 적재 |
| PATCH | `/api/products/{productId}/stock/remove` | 재고 차감 |
| PATCH | `/api/products/{productId}/view` | 조회수 증가 |
| GET | `/api/products/top?limit=10` | 인기 상품 (조회수 기준) |
| GET | `/api/products/top/sales?limit=10` | 인기 상품 (판매량 기준) |

### 주문 (Orders)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | 주문 생성 |
| GET | `/api/orders/{orderNumber}` | 주문 조회 |
| PATCH | `/api/orders/{orderNumber}/cancel` | 주문 취소 |

### 포인트 (Point)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/points/charge` | 포인트 충전 |
| GET | `/api/points/{userId}/balance` | 잔액 조회 |
| POST | `/api/points/refund` | 포인트 출금 |

### 쿠폰 (Coupon)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/coupons` | 쿠폰 생성 (대량) |
| POST | `/api/coupons/issue` | 쿠폰 발급 (ID 기반) |
| POST | `/api/coupons/issue-by-name` | 선착순 쿠폰 발급 |


## 🏗 아키텍처

### 레이어드 아키텍처

```
┌─────────────────────────────────────┐
│       Presentation Layer            │
│      (Controller)                   │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│       Application Layer             │
│       (UseCase/Service)             │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Domain Layer                │
│         (Entity)                    │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     Infrastructure Layer            │
│     (Repository/DB)                 │
└─────────────────────────────────────┘
```

### 디렉토리 구조

```
src/
├── main/
│   └── java/
│       └── com/choo/hhbackendlab/
│           ├── controller/          # Presentation Layer
│           │   ├── ProductController
│           │   ├── OrderController
│           │   ├── PointController
│           │   └── CouponController
│           │
│           ├── usecase/             # Application Layer
│           │   ├── product/
│           │   ├── order/
│           │   ├── point/
│           │   └── coupon/
│           │
│           ├── entity/              # Domain Layer
│           │   ├── Product
│           │   ├── Order
│           │   ├── OrderItem
│           │   ├── User
│           │   ├── PointWallet
│           │   ├── Coupon
│           │   ├── UserCoupon
│           │   ├── CouponIssueQueue
│           │   ├── QueueStatus
│           │   └── Category
│           │
│           ├── service/             # Queue 처리 서비스
│           │   └── CouponIssueQueueService
│           │
│           ├── scheduler/           # 스케줄러
│           │   └── CouponIssueQueueProcessor
│           │
│           ├── repository/          # Infrastructure Layer
│           │   ├── ProductRepository
│           │   ├── OrderRepository
│           │   ├── UserRepository
│           │   ├── PointWalletRepository
│           │   ├── CouponRepository
│           │   ├── UserCouponRepository
│           │   └── CouponIssueQueueRepository
│           │
│           └── dto/
│               ├── requestDto/
│               └── responseDto/
│
└── test/                            # Test Code
    ├── controller/
    ├── usecase/
    └── repository/
```
### 주요 엔티티

- **USERS**: 사용자 정보
- **PRODUCT**: 상품 정보 (조회수 포함)
- **ORDER**: 주문 정보
- **ORDER_ITEM**: 주문 상품 (다대다 중간 테이블)
- **POINT_WALLET**: 포인트 지갑 (낙관적 락)
- **COUPON**: 쿠폰 템플릿 (발급 가능 개수 관리)
- **USER_COUPON**: 사용자에게 발급된 쿠폰 (실제 발급 이력)
- **COUPON_ISSUE_QUEUE**: 쿠폰 발급 Queue (비동기 처리)
- **CATEGORY**: 상품 카테고리

## 🔒 동시성 제어

### 1. 포인트 시스템 - 낙관적 락 (Optimistic Lock)

```java
@Entity
public class PointWallet {
    @Version
    private Long version;  // 버전 필드
}
```

**동작 방식:**
- 충돌이 적을 것으로 예상되는 경우 사용
- 트랜잭션 커밋 시 버전 검증
- 충돌 발생 시 `OptimisticLockException` 발생

**장점:**
- DB 락을 사용하지 않아 성능 우수
- 데드락 발생 가능성 낮음

### 2. 쿠폰 시스템 - Queue 기반 비동기 처리 + 비관적 락

#### 아키텍처

```
[사용자 요청]
    ↓
[Queue에 추가 (PENDING)] ← 즉시 응답
    ↓
[100ms마다 스케줄러가 확인]
    ↓
[비관적 락으로 하나씩 처리 (PROCESSING)]
    ↓
[쿠폰 발급 및 couponCnt 감소]
    ↓
[상태 업데이트 (COMPLETED/FAILED)]
```

#### Queue 등록 (동기)

```java
public Long issueCouponByName(Long userId, String couponName) {
    // Queue에 요청만 추가하고 즉시 Queue ID 반환
    return queueService.addToQueue(userId, couponName);
}
```

#### Queue 처리 (비동기 - 스케줄러)

```java
@Scheduled(fixedDelay = 100)
public void processQueue() {
    queueService.processNextQueue();
}
```

#### 쿠폰 발급 (비관적 락)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM COUPON c WHERE c.name = :name AND c.couponCnt > 0")
Optional<Coupon> findFirstAvailableCouponByNameWithLock(@Param("name") String name);

public UserCoupon issueCoupon(User user) {
    this.couponCnt--;  // 재고 감소
    return new UserCoupon(this, user);
}
```

**동작 방식:**
1. **요청 단계**: 사용자 요청을 Queue에 저장하고 즉시 반환 (빠른 응답)
2. **처리 단계**: 스케줄러가 100ms마다 Queue에서 하나씩 꺼내서 처리
3. **동시성 제어**: 비관적 락으로 쿠폰 재고(couponCnt) 안전하게 감소
4. **상태 관리**: PENDING → PROCESSING → COMPLETED/FAILED

**장점:**
- 사용자는 빠른 응답을 받음 (Queue 등록만)
- 순차 처리로 안정적인 선착순 보장
- 멀티 서버 환경에서도 동작 (DB 비관적 락)
- 실패 시 재시도 가능

**특징:**
- Coupon: 템플릿 역할, couponCnt로 발급 가능 개수 관리
- UserCoupon: 실제 발급된 쿠폰 기록
- CouponIssueQueue: 발급 요청 Queue 관리

## 🧪 테스트

### 테스트 전략

- **단위 테스트**: Mockito를 사용한 비즈니스 로직 테스트
- **통합 테스트**: TestContainers를 사용한 실제 MySQL 환경 테스트
- **동시성 테스트**: ExecutorService를 활용한 멀티스레드 환경 테스트

### 주요 통합 테스트

#### Queue 기반 선착순 쿠폰 발급 테스트

```java
@SpringBootTest
@Testcontainers
public class CouponQueueIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40");

    @Test
    void 50명_동시_요청_선착순_처리() {
        // 50명이 동시에 쿠폰 발급 요청
        // → 모두 Queue에 등록
        // → 스케줄러가 순차적으로 처리
        // → 100개 중 50개 발급, 50개 남음
    }

    @Test
    void 쿠폰_소진_시_나머지_요청_실패() {
        // 50명이 10개 쿠폰 요청
        // → 10개 COMPLETED, 40개 FAILED
    }
}
```

### 테스트 커버리지

- **Controller 테스트**: MockMvc를 사용한 API 테스트
- **UseCase 테스트**: Mockito를 사용한 비즈니스 로직 테스트
- **Integration 테스트**: TestContainers + MySQL 통합 테스트
- **Repository 테스트**: @DataJpaTest를 사용한 데이터 액세스 테스트

### 테스트 구조

```
test/
├── integration/               # 통합 테스트 (TestContainers)
│   ├── CouponQueueIntegrationTest
│   └── OrderTest
│
├── controller/
│   ├── ProductControllerTest
│   ├── OrderControllerTest
│   ├── PointControllerTest
│   └── CouponControllerTest
│
├── usecase/
│   ├── product/
│   │   ├── CreateProductUseCaseTest
│   │   ├── IncrementProductViewCountUseCaseTest
│   │   ├── GetTopProductsUseCaseTest
│   │   └── GetTopProductsBySalesUseCaseTest
│   ├── order/
│   ├── point/
│   └── coupon/
│
└── repository/
    ├── ProductRepositoryTest
    ├── OrderRepositoryTest
    ├── PointWalletRepositoryTest
    ├── CouponRepositoryTest
    └── UserCouponRepositoryTest
```

### 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 실행
./gradlew test --tests "CouponQueueIntegrationTest"

# 통합 테스트만 실행
./gradlew test --tests "*.integration.*"
```
## ⚡ Caching 전략 

- ✅ 애플리케이션 시작과 동시에 캐시 준비 완료
- ✅ limit=15 요청 → TOP 20 캐시 사용 (캐시 히트)
- ✅ 주문 즉시 캐시 갱신으로 실시간 반영

  ---
🎯 캐싱 전략 정리

Cache-Aside Pattern (Lazy Loading)

- 캐시 우선 조회 → 없으면 DB 조회 후 캐싱
- TTL: 1시간 30분 (스케줄러 주기보다 여유있게 설정)

Scheduled Refresh (Proactive Caching)

- 매 1시간마다 자동 갱신 (@Scheduled)
- TOP 10, 20, 50을 미리 준비

Event-Driven Invalidation (실시간성 확보)

- 주문 완료 시 트랜잭션 커밋 후 즉시 갱신
- 비동기 처리로 성능 영향 최소화


## ⚡ 성능 최적화

### 성능 저하 가능 지점 식별

#### 1. 인기 상품 조회 (조회수 기반)

**문제 상황**:
- `viewCount` 컬럼으로 정렬하여 인기 상품 Top N 조회
- 인덱스 없이 전체 상품 테이블 스캔 후 정렬



**시나리오**:
- 상품 1,000개: 응답 시간 200ms
- 상품 10,000개: 응답 시간 2,000ms
- 상품 100,000개: 응답 시간 20,000ms (20초)

---

#### 2. 인기 상품 조회 (판매량 기반)

**문제 상황**:
- 최근 7일 판매량 계산을 위해 PRODUCT, ORDER_ITEM, ORDERS 3개 테이블 실시간 JOIN
- 날짜 범위 필터링을 위한 `orderedAt` 컬럼에 인덱스 없음
- 매 요청마다 GROUP BY 집계 연산 수행



**시나리오**:
- 주문 10,000건: 응답 시간 500ms
- 주문 100,000건: 응답 시간 3,000ms
- 주문 1,000,000건: 응답 시간 20,000ms 이상

---

#### 3. 주문번호 기반 주문 조회

**문제 상황**:
- `orderNumber` 컬럼에 UNIQUE 제약조건만 존재, 명시적 인덱스 미선언
- 주문 건수 증가 시 조회 성능 저하 가능성



**시나리오**:
- 주문 10,000건: 응답 시간 150ms
- 주문 100,000건: 응답 시간 1,500ms
- 주문 1,000,000건: 응답 시간 15,000ms

---

#### 4. 쿠폰 발급 Queue 모니터링

**문제 상황**:
- 스케줄러가 100ms마다 `status` 컬럼 기준 COUNT 쿼리 실행
- 인덱스 없이 전체 테이블 스캔
- 초당 10번, 시간당 36,000번의 불필요한 COUNT 쿼리



**시나리오**:
- Queue 1,000건: 쿼리당 50ms → 시간당 1,800초(30분) DB 부하
- Queue 10,000건: 쿼리당 500ms → 시간당 18,000초(5시간) DB 부하

---

#### 5. 상품-카테고리 N+1 쿼리

**문제 상황**:
- Product 엔티티의 Category 관계가 명시적 FetchType 선언 없음 (기본 EAGER)
- 상품 목록 조회 시 각 상품마다 카테고리 쿼리 별도 실행



**시나리오**:
- 상품 10개 조회: 11번의 쿼리 (1 + 10)
- 상품 50개 조회: 51번의 쿼리 (1 + 50)
- 상품 100개 조회: 101번의 쿼리 (1 + 100)

---

### 원인 분석

#### 원인 1: 인덱스 설계 부재

**주요 누락 인덱스**:

| 테이블 | 컬럼 | 용도 | 영향 |
|--------|------|------|------|
| PRODUCT | viewCount | 조회수 정렬 | Full Table Scan |
| ORDERS | orderedAt | 날짜 범위 검색 | 실시간 집계 비효율 |
| ORDERS | orderNumber | 주문 조회 | 명시적 인덱스 필요 |
| COUPON_ISSUE_QUEUE | status | 상태별 필터링 | COUNT 쿼리 비효율 |
| COUPON_ISSUE_QUEUE | (status, createdAt) | 복합 조건 조회 | 정렬 조회 비효율 |

**예상 개선 효과**:
- 조회 성능: 40-150배 개선
- DB CPU 사용률: 60-80% 감소

---

#### 원인 2: 실시간 집계 쿼리

**문제점**:
- 매 요청마다 최근 7일 주문 데이터를 실시간으로 JOIN 및 GROUP BY 집계
- 주문 데이터 증가에 따라 성능 선형 이상으로 저하
- 피크 시간대 DB 병목 현상 발생 가능

**데이터 증가 영향**:
- 주문 10,000건 → 100,000건: 6배 성능 저하
- 주문 100,000건 → 1,000,000건: 7배 성능 저하

---

#### 원인 3: N+1 쿼리 문제

**발생 원인**:
- `@ManyToOne` 관계에서 FetchType 미지정 시 기본값 EAGER 적용
- 상품 목록 조회 시 불필요한 카테고리 정보까지 자동 로딩

**영향**:
- 조회 건수에 비례한 쿼리 수 증가 (N+1)
- 네트워크 왕복 횟수 증가로 응답 시간 지연

---

#### 원인 4: 불필요한 모니터링 쿼리

**문제점**:
- 로그 출력을 위한 COUNT 쿼리가 비즈니스 로직보다 빈번히 실행
- 인덱스 없어 매번 Full Table Scan 발생
- DB 리소스 낭비

---

### 최적화 방안

#### 방안 1: 인덱스 설계 및 추가

**적용 대상**:
- Product.viewCount → 내림차순 인덱스
- Order.orderedAt → 내림차순 인덱스
- Order.orderNumber → Unique 인덱스
- CouponIssueQueue.status, createdAt → 복합 인덱스

**예상 효과**:
- 인기 상품 조회(조회수): 2,000ms → 50ms (40배 개선)
- 주문 번호 조회: 1,500ms → 10ms (150배 개선)
- Queue 상태 COUNT: 500ms → 5ms (100배 개선)

**구현 우선순위**: 즉시 적용 (소요 시간 1시간 이내)

---

#### 방안 2: 쿼리 재설계 - 배치 집계 방식

**적용 대상**: 판매량 기반 인기 상품 조회

**개선 전략**:
1. 일일 판매 통계 테이블(ProductSalesStats) 신규 생성
2. 매일 자정 배치 작업으로 전일 판매량 집계
3. 조회 시 미리 계산된 통계 데이터 활용

**장점**:
- 실시간 JOIN 제거
- 집계 연산을 오프피크 시간에 수행
- DB 부하 분산

**예상 효과**:
- 응답 시간: 3,000ms → 50ms (60배 개선)
- DB CPU 사용률: 80% → 20% (4배 감소)

**구현 우선순위**: 중기 개선 (소요 시간 2주)

---

#### 방안 3: 캐싱 전략 도입

**적용 대상**: 인기 상품 Top 10/20

**개선 전략**:
1. Redis를 활용한 조회 결과 캐싱
2. 5분 TTL 설정으로 주기적 갱신
3. 캐시 워밍업 전략 적용

**장점**:
- 첫 요청 이후 극적인 성능 개선
- DB 부하 90% 이상 감소
- 동시 접속자 증가에도 안정적 응답

**예상 효과**:
- 첫 요청: 3,000ms (DB 조회)
- 이후 요청: 5ms (Redis 캐시)
- 평균 응답 시간: 10ms 이하

**구현 우선순위**: 단기 개선 (소요 시간 1주)

---

#### 방안 4: Fetch 전략 개선

**적용 대상**: Product-Category 관계

**개선 전략**:
1. `@ManyToOne(fetch = FetchType.LAZY)` 명시적 선언
2. 필요한 경우에만 Fetch Join 사용
3. DTO 프로젝션으로 필요한 컬럼만 조회

**예상 효과**:
- 상품 목록 조회: 11번 쿼리 → 1번 쿼리 (11배 개선)
- 불필요한 네트워크 통신 제거
- 메모리 사용량 감소

**구현 우선순위**: 즉시 적용 (소요 시간 30분)

---

#### 방안 5: 모니터링 쿼리 최적화

**개선 전략**:
1. 불필요한 COUNT 쿼리 제거 또는 주기 조정 (100ms → 1분)
2. 인메모리 카운터 사용으로 DB 부하 제거
3. 필요 시 5분마다 DB와 동기화

**예상 효과**:
- DB 쿼리 수: 36,000번/시간 → 60번/시간 (600배 감소)
- DB 부하: 30분/시간 → 0.05분/시간

**구현 우선순위**: 즉시 적용 (소요 시간 30분)

---

### 구현 로드맵

#### Phase 1: 즉시 적용 (1주 이내)

| 작업 | 예상 공수 | 예상 효과 | 우선순위 |
|------|----------|----------|----------|
| Product.viewCount 인덱스 | 10분 | 40배 개선 | 최상 |
| Order.orderedAt 인덱스 | 10분 | 150배 개선 | 최상 |
| Product.category LAZY 설정 | 5분 | 11배 개선 | 높음 |
| 모니터링 쿼리 제거/주기 조정 | 15분 | 600배 감소 | 높음 |
| CouponIssueQueue 인덱스 | 10분 | 100배 개선 | 중간 |

**총 소요 시간**: 1시간
**예상 ROI**: 즉각적인 극적 성능 개선

---

#### Phase 2: 단기 개선 (1-2주)

| 작업 | 예상 공수 | 예상 효과 | 우선순위 |
|------|----------|----------|----------|
| Redis 캐싱 인프라 구축 | 4시간 | 300배 개선 | 최상 |
| 인기 상품 캐싱 적용 | 2시간 | DB 부하 90% 감소 | 최상 |
| 캐시 모니터링 구축 | 2시간 | 안정성 확보 | 중간 |

**총 소요 시간**: 1주
**예상 ROI**: 동시 접속자 처리 능력 10배 향상

---

#### Phase 3: 중기 개선 (1-2개월)

| 작업 | 예상 공수 | 예상 효과 | 우선순위 |
|------|----------|----------|----------|
| ProductSalesStats 테이블 설계 | 1주 | 근본적 해결 | 높음 |
| 배치 작업 스케줄러 구현 | 1주 | 60배 개선 | 높음 |
| 성능 테스트 및 검증 | 1주 | 안정성 확보 | 높음 |

**총 소요 시간**: 1-2개월
**예상 ROI**: 지속 가능한 성능 확보

---

### 예상 성능 개선 효과

#### 시나리오별 응답 시간 비교

| 기능 | 데이터 규모 | Before | After | 개선율 |
|------|------------|--------|-------|--------|
| 인기 상품(조회수) | 10,000개 | 2,000ms | 50ms | 40배 |
| 인기 상품(판매량) | 100,000건 | 3,000ms | 5ms | 600배 |
| 주문 조회 | 100,000건 | 1,500ms | 10ms | 150배 |
| 상품 목록 | 100개 | 1,100ms | 100ms | 11배 |

#### 전체 시스템 지표

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| 평균 응답 시간 | 1,500ms | 30ms | **50배** |
| DB CPU 사용률 | 80% | 20% | **4배 감소** |
| 동시 처리 가능 TPS | 100 | 3,000 | **30배** |
| 사용자 체감 성능 | 느림 | 즉각 반응 | **극적 개선** |

---

### 결론

**핵심 개선사항**:
1. 인덱스 5개 추가로 40-150배 성능 개선
2. 캐싱 도입으로 300-600배 응답 시간 단축
3. N+1 문제 해결로 불필요한 쿼리 제거
4. 배치 집계로 실시간 부하 감소

**권장 실행 순서**:
1. **1주차**: Phase 1 인덱스 추가 (즉각적 효과)
2. **2-3주차**: Phase 2 Redis 캐싱 (극적 개선)
3. **2-3개월**: Phase 3 배치 시스템 (장기 안정화)

**기대 효과**:
- 최소 투자(1시간)로 즉각적인 40-150배 성능 개선
- 단기 투자(1주)로 사용자 경험 획기적 향상
- 중기 투자(2개월)로 지속 가능한 고성능 시스템 구축

## 👥 기여자

- [@HyunJooong](https://github.com/HyunJooong)

---

**생성일**: 2025-11-06
**최종 업데이트**: 2025-11-13
**버전**: 2.0.0
