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
- **H2 Database** (개발 환경)

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
- ✅ 쿠폰 생성 (대량 생성 지원)
- ✅ 특정 쿠폰 ID로 발급
- ✅ 선착순 쿠폰 발급 (쿠폰명 기반)
- ✅ Synchronized를 통한 동시성 제어
- ✅ 쿠폰 사용 및 만료 관리

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
│           │   └── Category
│           │
│           ├── repository/          # Infrastructure Layer
│           │   ├── ProductRepository
│           │   ├── OrderRepository
│           │   ├── UserRepository
│           │   ├── PointWalletRepository
│           │   └── CouponRepository
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

- **USER**: 사용자 정보
- **PRODUCT**: 상품 정보 (조회수 포함)
- **ORDER**: 주문 정보
- **ORDER_ITEM**: 주문 상품 (다대다 중간 테이블)
- **POINT_WALLET**: 포인트 지갑 (낙관적 락)
- **COUPON**: 쿠폰 정보
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

### 2. 쿠폰 시스템 - Synchronized

```java
@Transactional
public synchronized Long issueCouponByName(Long userId, String couponName) {
    // 쿠폰 발급 로직
}
```

**동작 방식:**
- 메서드 레벨에서 동시 접근 제어
- 한 번에 하나의 스레드만 실행 가능

**장점:**
- 구현이 간단함
- 단일 JVM 환경에서 효과적

**주의사항:**
- **단일 서버 환경에서만 동작**
- 멀티 서버 환경(로드밸런싱)에서는 분산 락 필요
  - Redis (Redisson)
  - Database 비관적 락
  - Zookeeper

### 3. 이전 구현 - 비관적 락 (Pessimistic Lock)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM COUPON c WHERE c.user IS NULL AND c.name = :name")
Optional<Coupon> findFirstUnissuedCouponByNameWithLock(@Param("name") String name);
```

**동작 방식:**
- DB 레벨에서 행 단위 락 획득
- 트랜잭션이 끝날 때까지 다른 트랜잭션 대기```

### 테스트 커버리지

- **Controller 테스트**: MockMvc를 사용한 API 테스트
- **UseCase 테스트**: Mockito를 사용한 비즈니스 로직 테스트
- **Repository 테스트**: @DataJpaTest를 사용한 데이터 액세스 테스트

### 테스트 구조

```
test/
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
    └── CouponRepositoryTest
```
## 👥 기여자

- [@HyunJooong](https://github.com/HyunJooong)

---

**생성일**: 2025-11-06
**버전**: 1.0.0
