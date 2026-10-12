Expo-Attention-Server

[ 스타트업 박람회 프로젝트 | Server-V2 ] 참여 서비스 — 박람회 입장 스캔, 프로그램 출석, 현장 종이 QR 도메인을 담당한다.

## 환경 변수

로컬 값은 `.env.example`을 복사해 `.env`로 만들어 채운다. 토큰은 서버 간 호출 인증용이라 32자 이상이어야 하며, 짧으면 기동이 실패한다.

| 변수 | 설명 |
| --- | --- |
| `INTERNAL_TOKEN` | 이 서비스의 `/internal/**`를 보호하는 `X-Internal-Token` 값. 폼 서비스가 이 값을 `PARTICIPATION_SERVICE_INTERNAL_TOKEN`으로 들고 있다 |
| `USER_INTERNAL_TOKEN`, `EXPO_INTERNAL_TOKEN`, `APPLICATION_INTERNAL_TOKEN` | 유저·박람회·신청 서비스를 호출할 때 보내는 토큰. 각 서비스의 `INTERNAL_TOKEN`과 같은 값 |
| `USER_URL`, `EXPO_URL`, `APPLICATION_URL` | 비우면 Eureka로 찾는다. Gateway·Eureka 없이 로컬에서 직접 부를 때만 지정한다 |
| `ENTRY_RETRY_WINDOW_SECONDS` | 입장이 성공한 직후 같은 스캐너 사용자가 같은 QR을 다시 찍으면 처음 응답을 돌려주는 시간(초, 기본 `300`, `0`이면 끔) |
| `QR_ENTRY_REVIEW_ENABLED`, `QR_ENTRY_REVIEW_INTERVAL_MS` | 박람회 서비스 장애 때 기간 확인 없이 기록한 종이 QR 입장을 다시 확인하는 작업을 켜고 끄는 값(기본 `true`)과 주기(기본 5분) |
| `EXPO_PERIOD_CACHE_TTL_SECONDS` | 박람회 서비스에서 읽은 박람회 기간을 다시 부르지 않고 쓰는 시간(초, 기본 `30`, `0`이면 끔). 박람회 기간을 고친 직후 최대 이 시간만큼 이전 기간으로 판단될 수 있다 |
| `CANCELLATION_ABORTED_RETENTION_DAYS` | 중단된 취소 작업 기록(`tb_cancellation_aborted`)을 이 일수가 지나면 지운다(기본 `0` = 지우지 않음). 신청 서비스가 중단 뒤 늦게 보낼 수 있는 재시도 기간보다 충분히 길게 둔다. 다른 데이터는 기간으로 지우지 않는다(`docs/data-retention.md`) |
| `PREREGISTER_ENTRY_LEAD_MINUTES` | 사전등록 입장을 회차 시작 몇 분 전부터 허용할지 (기본 `30`) |
| `PROGRAM_ATTENDANCE_REQUIRE_CODE` | 켜면 일반 프로그램 출석 스캔에 참가자 `code`가 필수 (기본 `false`) |
| `SWAGGER_ENABLED` | 켜면 API 문서를 `/attendance/swagger-ui.html`, `/attendance/v3/api-docs`로 제공 (기본 `false`). 밖에서 보려면 게이트웨이 `publicPaths`에도 이 경로를 열어야 한다 |
| `SWAGGER_INCLUDE_INTERNAL` | 켜면 문서에 `/internal/**` 엔드포인트도 싣고 `X-Internal-Token` 헤더를 안내 (기본 `false`, `SWAGGER_ENABLED`가 켜져 있을 때만 의미가 있다) |

`/internal`은 게이트웨이 라우팅에 넣지 않는다. 게이트웨이가 모르는 경로는 404로 막아 주기 때문에 외부에서 닿지 않는다.

## 여러 인스턴스와 주기 작업

종이 QR 입장 재확인(`QrEntryPeriodReviewScheduler`)과 중단된 취소 기록 정리는 PostgreSQL 어드바이저리 락(`pg_try_advisory_xact_lock`)을 잡고 돈다. 재확인은 박람회마다 락을 잡으며, 다른 인스턴스가 이미 처리 중인 박람회는 기다리지 않고 건너뛴다. 락은 트랜잭션이 끝나거나 인스턴스가 죽어 연결이 끊기면 저절로 풀린다. 별도 의존성은 없다.

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

- 이전 태그의 빌드를 새 스키마의 데이터베이스에 실제로 띄워 보지는 않았다. 이전 빌드가 Flyway의 "적용됐지만 이 빌드에 없는 마이그레이션"을 무시하고 기동하는지는 Flyway 설정 기본값에 기대는 것이며 직접 확인하지 않았다. 롤백 전에 개발 서버에서 먼저 확인한다.
- `ddl-auto: validate`가 추가 컬럼·테이블을 허용한다는 점은 Hibernate의 일반 동작(매핑한 컬럼이 있는지만 검사)이며 이전 태그에서 직접 돌려 보지 않았다.
