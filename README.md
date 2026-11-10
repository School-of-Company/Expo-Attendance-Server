# Expo-Attention-Server

[ 스타트업 박람회 프로젝트 | Server-V2 ] 참여 서비스. 박람회 입장·퇴장 스캔, 프로그램 출석, 현장 종이 QR을 담당한다.

규칙은 `AGENTS.md`, 서비스 간 계약은 `docs/service-contracts.md`, 롤백 기준은 `docs/rollback.md`에 있다.

## 실행

```bash
cp .env.example .env          # 값을 채운다. 토큰은 32자 이상
docker compose up -d          # 로컬 PostgreSQL (127.0.0.1:15434)
set -a; . ./.env; set +a
./gradlew bootRun             # 스키마는 Flyway가 맞춘다
```

## 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `INTERNAL_TOKEN` | 필수 | `/internal/**` 호출 토큰 |
| `USER_INTERNAL_TOKEN` | 필수 | 유저 서비스 호출 토큰 |
| `EXPO_INTERNAL_TOKEN` | 필수 | 박람회 서비스 호출 토큰 |
| `APPLICATION_INTERNAL_TOKEN` | 필수 | 신청 서비스 호출 토큰 |
| `DB_PASSWORD` | 필수 | DB 비밀번호 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/expo_attention` | DB 주소 |
| `DB_USERNAME` | `expo_attention` | DB 계정 |
| `DB_POOL_SIZE` | `30` | DB 커넥션 풀 크기 |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | `3000` | 커넥션 대기 시간 |
| `TRANSACTION_TIMEOUT` | `15s` | 트랜잭션 제한 시간 |
| `SCAN_CONCURRENCY_RESERVE` | `6` | 스캔에 내주지 않고 남겨 둘 커넥션 수 |
| `SCAN_CONCURRENCY_WAIT_MS` | `500` | 스캔 자리 대기 시간 |
| `EUREKA_CLIENT_ENABLED` | `true` | Eureka 사용 여부 |
| `USER_URL` | 빈 값 | 유저 서비스 주소 (비우면 Eureka) |
| `EXPO_URL` | 빈 값 | 박람회 서비스 주소 (비우면 Eureka) |
| `APPLICATION_URL` | 빈 값 | 신청 서비스 주소 (비우면 Eureka) |
| `FEIGN_MAX_CONNECTIONS` | `200` | Feign 연결 풀 상한 |
| `FEIGN_MAX_CONNECTIONS_PER_ROUTE` | `50` | Feign 호스트별 연결 상한 |
| `FEIGN_TIME_TO_LIVE_SECONDS` | `900` | Feign 연결 수명 |
| `FEIGN_CONNECTION_REQUEST_TIMEOUT_MS` | `1000` | Feign 연결 대기 시간 |
| `PREREGISTER_ENTRY_LEAD_MINUTES` | `30` | 회차 시작 몇 분 전부터 입장 허용 |
| `ENTRY_REPLAY_WINDOW_SECONDS` | `30` | 재시도 스캔을 직전 결과로 돌려줄 시간 (`0`=끔) |
| `PROGRAM_ATTENDANCE_LEAD_MINUTES` | `10` | 프로그램 시작 몇 분 전부터 입실 허용 |
| `PROGRAM_ATTENDANCE_REQUIRE_CODE` | `false` | 프로그램 스캔에 `code` 필수 |
| `QR_ENTRY_REVIEW_ENABLED` | `true` | 기간 미확인 종이 QR 입장 재확인 |
| `QR_ENTRY_REVIEW_INTERVAL_MS` | `300000` | 재확인 주기 |
| `EXPO_PERIOD_CACHE_TTL_SECONDS` | `30` | 박람회 기간 캐시 (`0`=끔) |
| `PREREGISTER_SESSION_CACHE_TTL_SECONDS` | `30` | 회차 시간 캐시 (`0`=끔) |
| `ADMIN_STATS_CACHE_TTL_MILLIS` | `1000` | 현황 조회 캐시 (`0`=끔) |
| `KAFKA_EXPO_PROJECTION_ENABLED` | `false` | 박람회 프로젝션 사용 |
| `KAFKA_EXPO_PROJECTION_GROUP_ID` | `expo-attention-server.expo-changed` | 프로젝션 consumer group |
| `KAFKA_BOOTSTRAP_SERVERS` | 빈 값 | Kafka 주소 (프로젝션을 켜면 필수) |
| `ATTENDANCE_ADMIN_EVENTS_ENABLED` | `false` | 실시간 현황 SSE (켜면 DB 풀 1개 사용) |
| `ATTENDANCE_ADMIN_EVENTS_MAX_CONNECTIONS` | `500` | SSE 전체 연결 상한 |
| `ATTENDANCE_ADMIN_EVENTS_MAX_CONNECTIONS_PER_EXPO` | `50` | SSE 박람회별 연결 상한 |
| `ATTENDANCE_ADMIN_EVENTS_CONNECTION_LIFETIME` | `PT30M` | SSE 연결 수명 |
| `ATTENDANCE_ADMIN_EVENTS_HEARTBEAT_INTERVAL` | `PT25S` | SSE heartbeat 간격 |
| `ATTENDANCE_ADMIN_EVENTS_COALESCE_INTERVAL` | `PT1S` | SSE 신호 병합 간격 |
| `CANCELLATION_ABORTED_RETENTION_DAYS` | `0` | 중단된 취소 기록 보관 일수 (`0`=지우지 않음) |
| `SHUTDOWN_TIMEOUT` | `20s` | 종료 때 요청 대기 시간 |
| `METRICS_PORT` | 서비스 포트 | Prometheus·헬스 포트 |
| `SWAGGER_ENABLED` | `false` | API 문서 공개 |
| `SWAGGER_INCLUDE_INTERNAL` | `false` | 문서에 `/internal/**` 포함 |
| `LOGGING_HTTP_ACCESS_ENABLED` | `true` | 접근 로그 |
| `LOG_LEVEL_ROOT` | `info` | 전체 로그 레벨 |
| `LOG_LEVEL_CLIENT` | `info` | 다른 서비스 호출 로그 레벨 |
| `LOG_LEVEL_SCAN_REJECTION` | `info` | 스캔 거절 로그 레벨 |

## 헬스 체크

| 경로 | 내용 |
| --- | --- |
| `/actuator/health` | 배포 헬스체크 |
| `/actuator/health/liveness` | 프로세스 상태 |
| `/actuator/health/readiness` | 준비 상태와 DB 연결 |
