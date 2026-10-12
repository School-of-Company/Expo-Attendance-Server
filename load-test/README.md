# 입구 스캔 부하 테스트 (#102)

다른 서비스(유저·박람회·신청)를 **스텁**으로 대신하고, 참여 서비스의 입구 스캔 경로를 JMeter로 두들겨 지연과 처리량을 잰다. 실제 서비스나 운영 DB 없이
로컬에서 돌며, 다른 서비스가 느리거나 실패하는 조건도 스텁 환경 변수로 만들 수 있다.

## 준비

- Docker, Node.js, JMeter 5.6(`brew install jmeter`), Python 3(결과 요약).
- PostgreSQL: `DB_PASSWORD=... docker compose -p attn-load up -d postgres` (`compose.yaml`, 포트 15434). 끝나면 `docker compose -p attn-load down -v`.

## 실행

```bash
# 1) 다른 서비스 스텁(기본: 20~30ms 응답)
node load-test/stub-services.mjs

# 2) 참여 서비스(스텁을 가리키게 한다. 값은 로컬 전용)
export DB_URL=jdbc:postgresql://localhost:15434/expo_attention DB_USERNAME=expo_attention DB_PASSWORD=...
export INTERNAL_TOKEN=... USER_INTERNAL_TOKEN=... EXPO_INTERNAL_TOKEN=... APPLICATION_INTERNAL_TOKEN=...   # 각각 32자 이상
export USER_URL=http://127.0.0.1:18090 EXPO_URL=http://127.0.0.1:18090 APPLICATION_URL=http://127.0.0.1:18090
export EUREKA_CLIENT_ENABLED=false SERVER_PORT=18080
./gradlew bootRun

# 3) 부하(JMeter 비GUI) + 요약
SCENARIO=entry SCANNERS=20 DURATION=30 INTERNAL_TOKEN=$INTERNAL_TOKEN ./load-test/run-jmeter.sh
```

`run-jmeter.sh`는 시나리오 전에 스텁의 입장 기록을 비우고, 결과(JTL)를 라벨별로 건수·오류율·초당 요청·평균·p50/p90/p95/p99로 요약한다
(`summarize-jtl.py`). JMeter GUI로 `load-test/jmeter/scan.jmx`를 열어 고쳐도 된다.

### 시나리오(`SCENARIO`)

| 값 | 하는 일 | 기대 응답 |
|---|---|---|
| `entry` | 스캐너마다 서로 다른 참가자의 입장 스캔(`participantId`+`code`) | 200 |
| `entry-duplicate` | 스캐너마다 같은 참가자를 계속 찍음(재시도·중복 입장) | 200 또는 400 |
| `program` | 서로 다른 참가자의 일반 프로그램 출석 스캔 | 200 |
| `mixed` | 참가자 1~50에서 프로그램 스캔 80%와 취소 보호 acquire→abort 20%가 겹침 | 스캔 200·409, acquire 204·409 |

스텁 환경 변수: `STUB_LATENCY_MS`(기본 20), `STUB_JITTER_MS`(10), `STUB_FAIL_RATE`(0~1, 5xx 비율), `STUB_APPLICATION_EXTRA_MS`(신청 서비스만 더 느리게), `STUB_PORT`(18090).
`SCANNERS`(동시 스캐너 수), `DURATION`(초), `HOST`/`PORT`/`EXPO_ID`로 대상을 바꾼다.

## 측정 결과(2026-10-12, 개발 노트북 한 대에서 앱·스텁·DB·JMeter를 모두 돌린 값)

절대 수치는 이 환경의 값이라 서버 성능을 그대로 말하지 않는다. **조건 사이의 비율과 병목의 모양**을 본다. 운영 서버에서 같은 시나리오로 다시 재서 기준을 정한다.

### 다른 서비스 응답이 빠를 때(약 25ms)

| 시나리오 | 스캐너 | 초당 | p50 | p95 | p99 | 오류 |
|---|---|---|---|---|---|---|
| `entry` | 20 | 567 | 32ms | 45ms | 73ms | 0% |
| `entry` | 50 | 937 | 49ms | 76ms | 106ms | 0% |
| `entry-duplicate` | 20 | 574 | 32ms | 45ms | 74ms | 0%(200 20건, 나머지 400) |
| `program` | 20 | 233 | 82ms | 92ms | 101ms | 0% |
| `mixed`(스캔) | 20 | 219 | 82ms | 94ms | 142ms | 0% |
| `mixed`(acquire) | 20 | 55 | 5ms | 36ms | 41ms | 0%(204 160, 409 644) |

→ 정상일 때는 DB 커넥션 풀이 10개여도 병목이 아니다. 입구 스캐너 수십 대가 동시에 찍어도 p95가 100ms 아래다.

### 다른 서비스가 느릴 때(약 550ms) — 풀 크기에 따른 차이

입구 입장 스캔(`participantId`+`code`)은 취소 보호 락 안에서 유저 서비스를 부르는 동안 **DB 커넥션 하나를 쥔다**. 그래서 처리량이 "풀 크기 ÷ 호출 지연"에 묶인다.

| 풀 크기 | 스캐너 | 초당 | p50 | p95 | p99 | 오류 |
|---|---|---|---|---|---|---|
| 10(이전 기본) | 20 | 17 | 1112ms | 1353ms | 1558ms | 0% |
| 10 | 50 | 18 | 2750ms | 3311ms | 4073ms | 0.5%(500, 커넥션 대기 초과) |
| 10 | 80 | 20 | 3739ms | 4423ms | 5240ms | 14.6%(503, 커넥션 대기 초과) |
| **30(새 기본)** | 20 | 34 | 563ms | 610ms | 1111ms | 0% |
| **30** | 50 | 52 | 923ms | 1114ms | 1395ms | 0% |
| **30** | 100 | 53 | 1814ms | 2508ms | 3118ms | 0%(커넥션 대기 초과 1건) |

→ 풀 10개는 초당 약 20건에서 막혀 스캐너가 늘수록 대기가 쌓이고, 30개로 늘리면 처리량이 약 3배가 된다. 풀이 포화되면 이전에는 **500**("서버 오류")이었고, 이제는 **503**("요청이 몰려 잠시 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.")으로 알려 스캐너가 바로 다시 찍을 수 있다.

### 알게 된 것

- 다른 서비스 실패가 이어지면 회로 차단기가 열려(10초) 그동안 입장 스캔이 모두 503이 된다(`CallNotPermittedException`). 부하 중 스텁을 껐다 켜면 재현된다. 열림 시간 동안 현장에서 입장이 막힌다는 뜻이라, 유저 서비스 장애 때의 운영 절차(수동 입장 등)를 정해 두는 것이 좋다.
- 실패 원인(`DecodeException` 등)이 로그에 클래스 이름으로만 남아 스텁 응답 필드가 틀렸을 때 찾기 어려웠다 → #104.

## 정해진 설정(이 이슈)

| 설정 | 값 | 환경 변수 | 이유 |
|---|---|---|---|
| Hikari 최대 풀 크기 | 30 | `DB_POOL_SIZE` | 느린 다른 서비스에서도 처리량을 3배로. PostgreSQL 최대 연결 수(기본 100)와 다른 서비스가 쓰는 연결을 함께 봐야 한다 |
| 커넥션 대기 상한 | 3초 | `DB_POOL_CONNECTION_TIMEOUT_MS` | 기본 30초면 스캐너가 오래 매달린다. 3초 안에 503으로 알린다 |
| 트랜잭션 상한 | 15초 | `TRANSACTION_TIMEOUT` | 락·커넥션을 쥐는 최악의 스캔(다른 서비스 호출 읽기 3초 몇 번 + 락 대기 5초)보다 길게 |

상한 관계: Feign 연결 2초·읽기 3초(`spring.cloud.openfeign`) < 락 대기 5초(`CancellationGuardGate.LOCK_TIMEOUT`) < 트랜잭션 15초. 회로 차단기는 최근 20번 중 절반 이상 실패하거나 80% 이상이 2초보다 느리면 10초 열린다.

## 아직 정해지지 않은 것

- 행사장 실제 스캐너 수와 입장 피크(분당 건수) — 기준 시나리오의 `SCANNERS`를 정한다.
- 운영 PostgreSQL의 최대 연결 수와 참여 서비스 인스턴스 수 — 풀 30이 적절한지 정한다.
- 락 안의 원격 호출 자체를 줄이는 설계 변경이 필요한지(풀을 키우는 대신) — 위 수치로 판단해 별도 이슈로 다룬다.
