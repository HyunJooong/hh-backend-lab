# 마이크로서비스 아키텍처에서의 분산 트랜잭션 처리 설계 문서

---


### 1 모놀리식 아키텍처 (As-Is)

단일 애플리케이션 서버와 단일 데이터베이스를 사용하는 구조입니다.

| 구분 | 설명 |
|------|------|
| 애플리케이션 | Spring Boot 단일 애플리케이션 |
| 데이터베이스 | MySQL 단일 DB |
| 트랜잭션 | @Transactional (ACID 보장) |
| 장점 | 강력한 일관성, 구현 단순 |
| 단점 | 확장성 제한, 장애 전파 |

**주문 처리 플로우 (모놀리식)**

```java
@Transactional
public void createOrder(OrderRequest request) {
    // 1. 주문 생성
    Order order = orderRepository.save(new Order(request));
    
    // 2. 결제 처리
    Payment payment = paymentService.processPayment(order);
    
    // 3. 재고 차감
    inventoryService.decreaseStock(order.getItems());
    
    // 4. 포인트 차감
    pointService.deductPoints(order.getUserId());
    
    // 실패 시 자동 롤백 → 데이터 일관성 보장
}
```

### 2 마이크로서비스 아키텍처 (To-Be)

도메인별로 독립적인 서비스와 데이터베이스를 가지는 구조입니다.

| 서비스 | 데이터베이스 |
|--------|-------------|
| Order Service | Order DB (MySQL) |
| Payment Service | Payment DB (MySQL) |
| Inventory Service | Inventory DB (MySQL) |
| Point Service | Point DB (MySQL) |
| **통신 방식** | REST API / Kafka (비동기) |
| **트랜잭션** | 각 서비스 내부 로컬 트랜잭션만 보장 |

---

## 3. 분산 트랜잭션의 한계

### 3.1 ACID 트랜잭션 불가

마이크로서비스 환경에서는 단일 트랜잭션으로 여러 데이터베이스를 묶을 수 없습니다.

**문제 시나리오**

1. 주문 서비스에서 주문 생성 성공
2. 결제 서비스 호출 → 결제 성공
3. 재고 서비스 호출 → 재고 부족으로 실패
4. **결과: 결제는 완료되었지만 주문은 실패 (데이터 불일치) ⚠️**

### 3.2 Two-Phase Commit (2PC)의 한계

전통적인 분산 트랜잭션 프로토콜인 2PC는 마이크로서비스 환경에 부적합합니다.

| 문제점 | 설명 |
|--------|------|
| **블로킹** | 모든 참여자가 락을 유지하여 성능 저하 |
| **단일 장애점** | 코디네이터 장애 시 전체 시스템 중단 |
| **확장성 제한** | 참여자 증가 시 성능 급격히 저하 |
| **타임아웃 문제** | 네트워크 지연으로 불필요한 롤백 발생 |

> 따라서 분산 환경에서는 ACID 대신 **BASE(Basically Available, Soft state, Eventually consistent)** 원칙을 따라 **최종적 일관성(Eventual Consistency)**을 달성해야 합니다.

---

## 4. 해결 방안: Saga 패턴

### 4.1 Saga 패턴 개요

Saga 패턴은 여러 서비스에 걸친 트랜잭션을 로컬 트랜잭션의 연속으로 구현하며, 실패 시 **보상 트랜잭션(Compensating Transaction)**을 통해 이전 상태를 복원합니다.

**핵심 원칙**
- 각 서비스는 로컬 트랜잭션만 관리
- 실패 시 보상 트랜잭션으로 롤백
- 최종적 일관성 보장

### 4.2 구현 방식 비교

| 구분 | Choreography | Orchestration |
|------|-------------|--------------|
| **조정 방식** | 이벤트 기반 분산 조정 | 중앙 조정자 |
| **결합도** | 낮음 | 높음 |
| **흐름 파악** | 어려움 | 쉬움 |
| **디버깅** | 어려움 | 쉬움 |
| **장애점** | 분산 | 중앙 집중 |
| **권장 용도** | 단순한 흐름 | **복잡한 비즈니스 로직 (권장)** ✅ |

### 4.3 Orchestration 방식 상세 설계

중앙 조정자(Saga Orchestrator)가 전체 흐름을 관리하는 방식으로, 흐름 추적과 디버깅이 용이하여 실무에 권장됩니다.

#### 4.3.1 아키텍처 컴포넌트

| 컴포넌트 | 역할 |
|----------|------|
| **Saga Orchestrator** | 전체 Saga 흐름 관리, 서비스 호출 순서 제어 |
| **Saga Instance** | Saga 실행 상태 및 보상 트랜잭션 목록 관리 |
| **Command Handlers** | 각 서비스의 비즈니스 로직 실행 |
| **Compensation Handlers** | 보상 트랜잭션 실행 (롤백 로직) |

#### 4.3.2 주문 처리 흐름

**정상 플로우**
1. Orchestrator가 주문 생성 명령 → Order Service
2. 결제 처리 명령 → Payment Service
3. 재고 차감 명령 → Inventory Service
4. 포인트 차감 명령 → Point Service

**보상 플로우 (재고 차감 실패 시)**
1. Orchestrator가 보상 트랜잭션 시작
2. Payment Service: 결제 취소 (환불)
3. Order Service: 주문 상태를 CANCELLED로 변경


---

## 5. 보상 트랜잭션 구현 전략

### 5.1 Outbox 패턴

이벤트를 데이터베이스에 저장하여 메시지 유실을 방지하는 패턴입니다.

**핵심 메커니즘**
- 비즈니스 로직과 이벤트 발행을 단일 트랜잭션으로 처리
- 별도 Publisher가 Outbox 테이블을 폴링하여 이벤트 발행
- 발행 실패 시 재시도 메커니즘


**구현 예시**


*

### 5.2 Saga 상태 관리

Saga 실행 상태를 데이터베이스에 저장하여 재시작 및 복구를 지원합니다.


**상태 정의**

| 상태 | 설명 |
|------|------|
| STARTED | Saga 시작 |
| ORDER_CREATED | 주문 생성 완료 |
| PAYMENT_COMPLETED | 결제 완료 |
| STOCK_DECREASED | 재고 차감 완료 |
| COMPLETED | 전체 성공 |
| COMPENSATING | 보상 진행 중 |
| COMPENSATED | 보상 완료 (롤백 완료) |

**상태 관리 구현**

## 6. 실무 고려사항

### 6.1 멱등성 (Idempotency)

동일한 요청이 여러 번 수행되어도 결과가 동일해야 합니다.

**구현 방법**
- 고유 키 기반 중복 체크 (Order ID, Payment ID)
- 상태 체크 후 작업 수행 (이미 REFUNDED면 스킵)
- 데이터베이스 유니크 제약조건 활용

**예시: 멱등한 환불 처리**


### 6.2 타임아웃 관리

각 서비스 호출마다 적절한 타임아웃을 설정하여 무한 대기를 방지합니다.

| 서비스 | 권장 타임아웃 | 이유 |
|--------|--------------|------|
| Order Service | 3초 | DB 쓰기만 수행 |
| Payment Service | 10초 | 외부 PG 연동 |
| Inventory Service | 5초 | 락 대기 가능 |
| Point Service | 3초 | 단순 DB 연산 |



### 6.3 보상 실패 처리

보상 트랜잭션도 실패할 수 있으므로 재시도 및 알림 메커니즘이 필요합니다.

**전략**
- 지수 백오프(Exponential Backoff) 재시도
- 최대 재시도 횟수 제한 (예: 3회)
- 실패 시 Dead Letter Queue로 이동
- 운영팀 알림 (Slack, Email) 및 수동 처리

---

## 7. 모니터링 및 운영

### 7.1 필수 메트릭

| 메트릭 | 설명 |
|--------|------|
| **Saga 성공률** | 전체 Saga 중 성공한 비율 (목표: 99.9% 이상) |
| **보상 실행 횟수** | 보상 트랜잭션 실행 빈도 추적 |
| **평균 처리 시간** | Saga 시작부터 완료까지 걸린 시간 |
| **보상 실패율** | 보상 트랜잭션 실패 비율 (즉시 알림 필요) |
| **타임아웃 발생** | 각 서비스별 타임아웃 발생 횟수 |



### 7.2 로깅 전략

분산 추적을 위해 **상관 ID(Correlation ID)**를 모든 로그에 포함합니다.


**구조화된 로깅**

### 7.3 대시보드 구성

**Grafana 대시보드 패널**
- Saga 성공/실패 비율 (파이 차트)
- 시간대별 Saga 실행 수 (라인 차트)
- 서비스별 평균 응답 시간 (바 차트)
- 보상 실행 빈도 (히트맵)
- 현재 진행 중인 Saga 수 (게이지)

---

## 8. 단계별 마이그레이션 전략

### Phase 1: 준비 단계 (1-2개월)

- Outbox 테이블 및 Saga 상태 테이블 생성
- Kafka 클러스터 구축 및 테스트
- 모니터링 도구 설정 (Prometheus, Grafana)
- 팀 교육 및 POC 진행

### Phase 2: 서비스 분리 (2-3개월)

- Order Service 분리 및 독립 DB 구성
- Payment Service 분리
- Inventory Service 분리
- Orchestrator 구현 및 통합 테스트
- 카나리 배포로 점진적 트래픽 전환 (10% → 50% → 100%)

### Phase 3: 최적화 및 안정화 (1-2개월)

- 성능 튜닝 및 병목 지점 개선
- 장애 시나리오 테스트 (Chaos Engineering)
- 운영 매뉴얼 작성
- 온콜 프로세스 수립
- 사후 분석 및 개선 사항 반영

