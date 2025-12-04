  ---
Redis Sorted Set 기반 선착순 쿠폰 발급 시스템 기술 보고서

1. 개요

본 프로젝트는 Redis Sorted Set을 활용하여 대용량 트래픽 환경에서의 선착순 쿠폰 발급 시스템을 구현하였습니다. 기존 데이터베이스 기반 락(Lock) 방식의 성능 한계를 극복하고, 높은 동시성 환경에서도 안정적으로 동작하는 비동기
대기열 시스템을 구축했습니다.

  ---
2. 시스템 아키텍처

2.1 전체 흐름도

[사용자 요청]
↓
[IssueCouponUseCase] - 쿠폰 발급 요청 접수
↓
[CouponIssue Service] - Redis Sorted Set에 대기열 추가
↓ (비동기)
[CouponIssueProcessor] - 스케줄러가 50ms마다 대기열 처리
↓
[실제 쿠폰 발급] - DB 트랜잭션으로 쿠폰 발급
↓
[발급 완료 마킹] - Redis에 발급 완료 기록

2.2 핵심 컴포넌트

1. CouponIssue (CouponIssue.java:36)
   - Redis Sorted Set 기반 대기열 관리 서비스
   - 쿠폰 발급 요청 추가, 처리, 조회 기능 제공
2. CouponIssueProcessor (CouponIssueProcessor.java:18)
   - 스케줄러 기반 비동기 처리 컴포넌트
   - 50ms 간격으로 대기열을 확인하여 순차 처리
3. IssueCouponUseCase (IssueCouponUseCase.java:19)
   - 비즈니스 로직 레이어
   - 일반 발급과 선착순 발급 두 가지 방식 지원

  ---
3. Redis 데이터 구조

3.1 키(Key) 설계

프로젝트에서는 메모리 최적화를 위해 짧은 키 접두사를 사용합니다:

cpn:wl:{couponName}          - 쿠폰 발급 대기열 (Sorted Set)
cpn:isu:{couponName}:{userId} - 발급 완료 여부 체크 (String)

설계 근거:
- cpn: coupon의 약어로 네임스페이스 구분
- wl: waiting list의 약어
- isu: issued의 약어
- 짧은 키 사용으로 Redis 메모리 사용량 최적화

3.2 Sorted Set 구조

Key: cpn:wl:신규가입쿠폰
Score (정렬 기준): System.nanoTime() - 나노초 단위 타임스탬프
Member (데이터): userId (String)

Sorted Set 선택 이유:
- 자동 정렬: Score(타임스탬프) 기준 자동 정렬로 선착순 보장
- 빠른 조회: O(log N) 시간 복잡도로 순위 조회
- 원자적 연산: ZADD, ZRANGE, ZREM 등 원자적 명령어 제공
- 중복 방지: 동일 Member는 자동으로 중복 제거

3.3 TTL 설정

모든 Redis 키에 7일 TTL을 설정하여 메모리 누수 방지:

private static final long TTL_DAYS = 7;
redisTemplate.expire(waitingListKey, TTL_DAYS, TimeUnit.DAYS);

  ---
4. 핵심 기능 상세 분석

4.1 대기열 추가 (addToWaitingList)

위치: CouponIssue.java:59

처리 흐름:

1. 중복 발급 체크 (2단계 검증)
   // 1단계: Redis에서 빠른 체크
   Boolean alreadyIssued = redisTemplate.hasKey(issuedKey);

// 2단계: DB에서 최종 검증 (Redis 장애 대비)
boolean alreadyIssuedInDB = userCouponRepository
.existsByUserIdAndCouponId(userId, coupon.getId());
2. Redis Pipeline을 통한 원자적 처리
   redisTemplate.executePipelined(new SessionCallback<Object>() {
   public Object execute(RedisOperations operations) {
   zSetOps.add(waitingListKey, userId.toString(), score);
   operations.expire(waitingListKey, TTL_DAYS, TimeUnit.DAYS);
   return null;
   }
   });

2. Pipeline 사용 이유:
   - 여러 Redis 명령어를 하나의 네트워크 왕복으로 처리
   - 네트워크 오버헤드 최소화
   - 원자성 보장 (중간 상태 노출 방지)
3. 대기열 순서 반환
   Long rank = zSetOps.rank(waitingListKey, userId.toString());
   return rank != null ? rank : 0L;

시간 복잡도: O(log N) - Sorted Set의 ZADD, ZRANK 연산

4.2 대기열 처리 (processNextInWaitingList)

위치: CouponIssue.java:169

처리 흐름:

1. 가장 앞의 요청 조회
   Set<String> members = zSetOps.range(waitingListKey, 0, 0);
   - ZRANGE key 0 0: Score가 가장 작은(가장 먼저 요청한) 1개 조회
2. 대기열에서 즉시 제거
   Long removed = zSetOps.remove(waitingListKey, userIdStr);
   if (removed == null || removed == 0) {
   return false; // 다른 프로세스가 이미 처리
   }
   - 원자적 제거로 동시 처리 방지
   - 분산 환경에서도 안전
3. 실제 쿠폰 발급 (DB 트랜잭션)
   Long issuedCouponId = issueCoupon(userId, couponName);
4. 발급 완료 마킹
   String issuedKey = ISSUED_KEY_PREFIX + couponName + ":" + userId;
   redisTemplate.opsForValue().set(issuedKey, "1", TTL_DAYS, TimeUnit.DAYS);

4.3 스케줄러 비동기 처리

위치: CouponIssueProcessor.java:30

@Scheduled(fixedDelay = 50)
public void processWaitingList() {
boolean processed = couponIssue.processNextRequest();
// 처리 완료 후 50ms 대기 후 다시 실행
}

설계 특징:
- fixedDelay = 50ms: 이전 작업 완료 후 50ms 후 재실행
- 빠른 처리 주기: Redis의 빠른 성능을 활용
- 논블로킹: 처리할 요청이 없으면 즉시 반환

처리 용량 계산:
- 1초당 최대 20회 스케줄 실행 (1000ms / 50ms)
- 평균 DB 쿠폰 발급 시간 100ms 가정 시
- 이론적 처리량: 초당 약 10건

  ---
5. 동시성 제어 전략

5.1 Redis 레벨 동시성 제어

Redis Sorted Set의 원자적 연산 활용:
// ZADD: 원자적으로 추가, 중복 시 score만 업데이트
zSetOps.add(waitingListKey, userId.toString(), score);

// ZREM: 원자적으로 제거, 이미 제거된 경우 0 반환
Long removed = zSetOps.remove(waitingListKey, userIdStr);

