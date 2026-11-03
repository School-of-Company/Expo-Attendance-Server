# Expo-Attention-Server

[ 스타트업 박람회 프로젝트 | Server-V2 ] 참여 서비스 — 박람회 입장 스캔, 프로그램 출석, 현장 종이 QR 도메인을 담당한다. 규칙과 서비스 간 계약은 `AGENTS.md`와 `docs/service-contracts.md`에 있다.

## 실행

1. `.env.example`을 `.env`로 복사해 채운다. 토큰은 서버 간 호출 인증용이라 32자 이상이어야 하며, 짧으면 기동이 실패한다.
2. `docker compose up -d`로 로컬 PostgreSQL(`127.0.0.1:15434`)을 띄운다.
3. `.env` 값을 환경변수로 올린 뒤(`set -a; . ./.env; set +a`) `./gradlew bootRun`. 스키마는 Flyway가 기동 중에 맞춘다.

## 환경 변수

필수:

| 변수 | 설명 |
| --- | --- |
| `INTERNAL_TOKEN` | 이 서비스의 `/internal/**`를 보호하는 `X-Internal-Token` 값. `/internal`을 부르는 박람회·신청·리포트 서비스가 같은 값을 들고 있다 |
| `USER_INTERNAL_TOKEN`, `EXPO_INTERNAL_TOKEN`, `APPLICATION_INTERNAL_TOKEN` | 유저·박람회·신청 서비스를 부를 때 보내는 토큰. 각 서비스의 `INTERNAL_TOKEN`과 같은 값 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 접속 정보. 기본값은 `jdbc:postgresql://localhost:5432/expo_attention`, `expo_attention`이고 `DB_PASSWORD`는 기본값이 없다 |

선택(괄호 안이 기본값):

| 변수 | 설명 |
| --- | --- |
| `EUREKA_CLIENT_ENABLED` | `false`면 Eureka에 등록하지도 찾지도 않는다(로컬 개발용, `eureka.client.enabled`). 이때는 아래 `*_URL`을 지정한다 |
| `USER_URL`, `EXPO_URL`, `APPLICATION_URL` | 비우면 Eureka로 찾는다. Gateway·Eureka 없이 직접 부를 때만 지정 |
| `QR_ENTRY_REVIEW_ENABLED` (`true`), `QR_ENTRY_REVIEW_INTERVAL_MS` (5분) | 박람회 서비스 장애 때 기간 확인 없이 기록한 종이 QR 입장을 다시 확인하는 작업과 그 주기 |
| `ENTRY_REPLAY_WINDOW_SECONDS` (`30`, `0`=끔) | 입장 뒤 이 시간 안에 같은 스캐너가 같은 QR을 퇴장 없이 다시 찍으면 400 대신 직전 결과를 돌려준다(타임아웃 뒤 재시도, #176) |
| `EXPO_PERIOD_CACHE_TTL_SECONDS` (`30`, `0`=끔) | 박람회 기간 캐시 시간. 기간을 고친 직후 최대 이 시간만큼 이전 기간으로 판단될 수 있다 |
| `PREREGISTER_SESSION_CACHE_TTL_SECONDS` (`30`, `0`=끔) | 박람회 서비스에서 읽은 사전등록 회차 시간 캐시 시간(#249). 회차 시간을 고친 직후 최대 이 시간만큼 이전 시간으로 판정될 수 있다. 프로젝션에서 읽은 값은 캐시하지 않는다 |
| `ADMIN_STATS_CACHE_TTL_MILLIS` (`1000`, `0`=끔) | 입·퇴장 현황(summary·hourly·recent·scanners) 결과를 대시보드끼리 함께 쓰는 시간(#255). 관리자 변경 신호를 켜면 신호를 받는 즉시 그 박람회 결과를 버린다 |
| `SCAN_CONCURRENCY_RESERVE` (`6`), `SCAN_CONCURRENCY_WAIT_MS` (`500`) | 동시 스캔 수를 `DB_POOL_SIZE - reserve`로 제한한다(#247: 스캔이 게이트 트랜잭션 안에서 커넥션을 하나 더 요구해 풀에 닿으면 교착). 대기 시간 안에 자리를 못 얻으면 커넥션 없이 503. reserve가 1보다 작으면 기동하지 않고, 허용 수가 2보다 작으면 경고를 남긴다 |
| `KAFKA_EXPO_PROJECTION_ENABLED` (`false`) | 켜면 `expo.expo.changed`로 받은 박람회 기간·프로그램·회차 시간을 로컬 프로젝션에서 먼저 읽고, 없으면 동기 호출로 넘어간다. `KAFKA_BOOTSTRAP_SERVERS`가 없으면 기동 실패. consumer group `KAFKA_EXPO_PROJECTION_GROUP_ID`(`expo-attention-server.expo-changed`), 실패 메시지는 `expo.expo.changed.dlt`. 켠 뒤 박람회 서비스 `POST /internal/expo-events/republish`로 전체 상태를 한 번 받는다. 자세한 동작은 `docs/service-contracts.md` |
| `KAFKA_BOOTSTRAP_SERVERS` (빈 값) | Kafka 브로커 주소. 프로젝션을 켤 때만 필요 |
| `PREREGISTER_ENTRY_LEAD_MINUTES` (`30`) | 사전등록 입장을 회차 시작 몇 분 전부터 허용할지 |
| `PROGRAM_ATTENDANCE_REQUIRE_CODE` (`false`) | 켜면 일반 프로그램 출석 스캔에 참가자 `code`가 필수 |
| `PROGRAM_ATTENDANCE_LEAD_MINUTES` (`10`) | 일반 프로그램 입실(첫 스캔)을 시작 몇 분 전부터 받을지. 종료 시각까지 받고, 퇴실은 시각을 보지 않는다 |
| `ATTENDANCE_ADMIN_EVENTS_ENABLED` (`false`) | 실시간 입·퇴장 현황 SSE(`GET /attendance/summary/{expoId}/events`)와 DB LISTEN 연결. 한도·수명·heartbeat·병합 간격(`..._MAX_CONNECTIONS` `500`, `..._MAX_CONNECTIONS_PER_EXPO` `50`, `..._CONNECTION_LIFETIME` `PT30M`, `..._HEARTBEAT_INTERVAL` `PT25S`, `..._COALESCE_INTERVAL` `PT1S`)은 `docs/admin-data-change-events.md` |
| `CANCELLATION_ABORTED_RETENTION_DAYS` (`0`=지우지 않음) | 중단된 취소 작업 기록(`tb_cancellation_aborted`)을 이 일수가 지나면 지운다. 신청 서비스의 늦은 재시도 기간보다 충분히 길게 둔다. 다른 데이터는 기간으로 지우지 않는다(`docs/data-retention.md`) |
| `SHUTDOWN_TIMEOUT` (`20s`) | graceful shutdown 때 처리 중인 요청을 기다리는 시간. 스캔 한 번보다 길게 둔다 |
| `METRICS_PORT` (서비스 포트) | `/actuator/prometheus`와 헬스 경로를 별도 내부 포트에서 제공. Eureka 메타데이터 `prometheus.port`에도 실린다 |
| `DB_POOL_SIZE` (`30`), `DB_POOL_CONNECTION_TIMEOUT_MS` (`3000`) | 커넥션 풀 크기와 빈 커넥션 대기 시간(ms). 넘으면 503. 입장 스캔은 다른 서비스를 부르는 동안 커넥션을 하나 잡는다(`load-test/README.md`) |
| `TRANSACTION_TIMEOUT` (`15s`) | 트랜잭션 기본 제한 시간. 넘으면 503 |
| `LOGGING_HTTP_ACCESS_ENABLED` (`true`) | 요청마다 접근 로그 한 줄(`module`=`HttpAccess`). 형식은 `AGENTS.md`의 Logging |
| `LOG_LEVEL_ROOT` (`info`) | 전체 로그 레벨 |
| `LOG_LEVEL_CLIENT` (`info`) | 다른 서비스 호출(`team.startup.expo.global.client`) 로그. 임시로 `debug`면 회로가 열려 차단된 호출도 호출마다 남는다 |
| `LOG_LEVEL_SCAN_REJECTION` (`info`) | 스캔 거절 로그(`ScanRejection`). 입구에서 시끄러우면 `warn`으로 올려 끈다 |
| `SWAGGER_ENABLED` (`false`) | API 문서를 `/attendance/swagger-ui.html`, `/attendance/v3/api-docs`로 제공. 밖에서 보려면 게이트웨이 `publicPaths`에도 열어야 한다 |
| `SWAGGER_INCLUDE_INTERNAL` (`false`) | 문서에 `/internal/**`와 `X-Internal-Token` 안내도 싣는다(`SWAGGER_ENABLED`가 켜져 있을 때만) |

`/internal`은 게이트웨이 라우팅에 넣지 않는다. 게이트웨이가 모르는 경로는 404로 막혀 외부에서 닿지 않는다.

## 여러 인스턴스와 주기 작업

종이 QR 입장 재확인(`QrEntryPeriodReviewScheduler`)과 중단된 취소 기록 정리는 PostgreSQL 어드바이저리 락(`pg_try_advisory_xact_lock`)을 잡고 돈다. 재확인은 박람회마다 락을 잡고, 다른 인스턴스가 처리 중인 박람회는 건너뛴다. 락은 트랜잭션이 끝나거나 연결이 끊기면 풀린다. 별도 의존성은 없다.

## 헬스 프로브

| 경로 | 내용 |
| --- | --- |
| `/actuator/health` | 기존 배포 헬스체크 경로(서비스 포트, 인증 없음) |
| `/actuator/health/liveness` | 프로세스 상태만 본다 |
| `/actuator/health/readiness` | 준비 상태와 DB 연결(`db`)만 본다. Flyway는 기동 중에 끝나야 준비 상태가 되므로 따로 넣지 않았다 |

유저·박람회·신청 서비스는 준비 상태에 넣지 않는다. 다른 서비스가 내려갔다고 이 서비스가 트래픽에서 빠지면 안 되기 때문이다(그 경우는 회로 차단기와 503으로 다룬다). `METRICS_PORT`를 지정하면 이 경로들도 그 포트에서 제공된다.

## 롤백

새 버전에 문제가 있어 이전 버전으로 되돌릴 때의 기준이다. Flyway 마이그레이션은 되돌리지 않는다. **새 테이블과 컬럼은 그대로 두고, 급하게 지우지 않는다.**

확인한 것(마이그레이션 SQL과 태그별 코드를 읽었다):

- V9~V12 중 테이블·컬럼을 더하는 것은 V9(`tb_preregister_session.revision`, 기본값 0), V10(`tb_cancellation_guard`), V11(`tb_cancellation_aborted`), V12(`tb_cancellation_guard.cancel_revision`, NULL 허용)이다. 이전 버전 엔티티는 이 컬럼과 테이블을 매핑하지 않으므로 쓰지 않고, 이전 버전이 넣는 행은 기본값·NULL로 채워진다.
- 태그별 마이그레이션: v0.1.1은 V1~V8, v0.2.0·v0.2.1은 V1~V10, v0.2.2는 V11까지, v0.2.3 이후는 V12까지다. 그래서 v0.2.x 사이의 롤백은 새로 생긴 V11·V12 객체를 이전 코드가 쓰지 않는 것으로 끝난다. v0.2.0의 `PreregisterSession`은 이미 `revision`과 NULL 허용 `session_id`를 매핑한다.
- **예외: V9는 추가만이 아니다.** `tb_preregister_session.session_id`의 NOT NULL을 풀었다(회차 없이 취소만 먼저 도착한 기록). v0.1.1의 엔티티는 `session_id`를 NOT NULL로 가정하므로, v0.1.1 이전으로 되돌리면 그런 행을 읽을 때 실패할 수 있다. v0.2.0 이후로의 롤백에는 해당하지 않는다.
- v0.2.0·v0.2.1로 롤백하면 취소 상태 조회 API(#92)와 중단 기록(V11)이 없다. 신청 서비스가 그 API를 부르고 있으면 오류가 나므로 신청 서비스 쪽도 함께 확인한다. 이전 버전의 박람회 삭제는 `tb_cancellation_aborted` 행을 지우지 못해 남는다(`DeleteExpoDataServiceImpl`의 해당 한 줄이 v0.2.0 이후에 추가됐다).

확인하지 못한 것:

- V13 이후 마이그레이션(V13~V25)은 롤백 관점에서 검토하지 않았다. 위 판단은 V9~V12에만 해당한다.
- 이전 태그의 빌드를 새 스키마의 데이터베이스에 실제로 띄워 보지는 않았다. 이전 빌드가 Flyway의 "적용됐지만 이 빌드에 없는 마이그레이션"을 무시하고 기동하는지는 Flyway 설정 기본값에 기대는 것이며 직접 확인하지 않았다. 롤백 전에 개발 서버에서 먼저 확인한다.
- `ddl-auto: validate`가 추가 컬럼·테이블을 허용한다는 점은 Hibernate의 일반 동작(매핑한 컬럼이 있는지만 검사)이며 이전 태그에서 직접 돌려 보지 않았다.
