# 실시간 입·퇴장 현황 변경 SSE (#141)

실시간 입·퇴장 모니터링 화면이 입장·퇴장, 기록상 체류, 시간대 추이, 최근 처리, 스캐너별 현황을 다시 읽도록 알리는 신호다. 형식과 연결 관리는 Expo-Application-Server #162(`docs/admin-data-change-events.md`)와 같다. 이벤트는 화면 갱신 신호일 뿐이며 입장·퇴장 처리의 실행 조건이 아니다. 이벤트마다 현황 값을 계산해 보내지 않는다.

## 구독

`GET /attendance/summary/{expoId}/events` (`Accept: text/event-stream`)

- 기존 현황 조회(`/attendance/summary/{expoId}` 등)와 같은 관리자 계약이다. 게이트웨이가 JWT를 확인하고 넘긴 `X-User-Role`이 `ROLE_ADMIN`이어야 한다. 행사별 관리자 권한은 기존 현황 GET과 마찬가지로 이 서비스가 따로 확인하지 않는다.
- 인증정보를 URL query로 받지 않는다. query가 있으면 `400`이다. 브라우저 `EventSource`는 헤더를 보낼 수 없으므로 Client는 fetch 기반 SSE 또는 BFF가 헤더를 붙여 전달한다.
- 응답 헤더: `Content-Type: text/event-stream`, `Cache-Control: no-store`, `X-Accel-Buffering: no`.
- `400` 박람회 ID 형식(소문자 UUID) 또는 query, `401` 역할 헤더 없음, `403` `ROLE_ADMIN` 아님, `503` 연결 수 한도 초과. 꺼져 있으면 `404`.

## 이벤트

```
id:3f0c...
event:admin-data-changed
data:{"eventId":"3f0c...","expoId":"...","resource":"ATTENDANCE","occurredAt":"2026-10-17T09:00:00Z"}
```

- `data`는 `eventId`, `expoId`, `resource`, `occurredAt`뿐이다. 이름·전화번호·참가자 코드·종이 QR 토큰·스캐너 이름을 담지 않는다. 이 서비스는 `ATTENDANCE`만 보낸다(`APPLICATIONS`는 Application 몫).
- 받으면 기존 GET(`/attendance/summary/{expoId}`, `/hourly`, `/recent`, `/scanners`, `/walk-in-counts/{expoId}`)을 현재 필터(`date`/`all`)로 다시 조회한다. 숫자를 직접 증감하지 않는다. `stillInside`는 기록상 체류이며 실제 현장 인원이 아니다.
- heartbeat는 SSE 주석(`:heartbeat`, 기본 25초)이며 데이터 신호가 아니다. 연결 직후 첫 heartbeat로 응답 헤더를 바로 보낸다.

## 병합과 조회 제한

- 같은 박람회 변경은 `coalesce-interval`(기본 1초) 동안 모아 한 번만 보낸다. 첫 변경 뒤 그 시간이 지나면 보내므로 화면 반영은 최대 그만큼 늦다. 신호를 버리지는 않는다.
- 연결마다 보내지 않은 이벤트는 최신 하나만 남기므로 느린 수신자에게도 쌓이지 않는다.
- Client는 재조회가 진행 중일 때 온 이벤트를 하나로 모아, 끝난 뒤 한 번만 다시 조회한다(동시에 여러 번 조회하지 않는다).

## 재동기화

- 최초 연결과 재연결 직후에는 기존 GET으로 전체 현재값을 다시 읽는다. 이벤트를 보관·재생하지 않으며 `Last-Event-ID`는 쓰지 않는다.
- 중복 이벤트나 연결 유실이 있어도 재조회만 하므로 값이 영구히 어긋나지 않는다.
- 서버의 DB 신호 연결이 끊겼다 복구되면 그 사이 신호를 잃었을 수 있으므로 모든 구독자에게 `admin-data-changed`를 한 번 보낸다.
- 배포·종료 때 서버가 연결을 먼저 닫는다. Client는 재연결 후 GET으로 다시 읽는다.

## 발행 기준

V18 트리거가 현황 GET이 읽는 표의 실제 변화에서 `pg_notify('attendance_admin_changes', expoId)`를 보낸다. 코드 경로마다 발행을 호출하지 않으므로 등록 QR(일반 참가자·연수자)·전화번호 입장·종이 QR·퇴장·간편입장·박람회 삭제 정리가 모두 같은 기준을 따른다.

| 테이블 | 신호 |
| --- | --- |
| `tb_entry_log` | 추가·삭제, `exited_at` 변경 |
| `tb_walk_in_log` | 추가·삭제 |

- PostgreSQL NOTIFY는 커밋된 트랜잭션만 전달하고 롤백되면 버린다. 같은 트랜잭션의 같은 박람회 신호는 한 번으로 합쳐진다.
- 같은 날 두 번째 스캔(입장 로그 `ON CONFLICT DO NOTHING`), 같은 `requestId`의 간편입장 재전송, 실패한 스캔은 행이 바뀌지 않아 신호를 만들지 않는다.
- 프로그램 출석(`tb_standard_program_attendance`, `tb_training_program_attendance`)은 실시간 입·퇴장 현황에 들어가지 않고 박람회 ID도 없어 신호를 보내지 않는다. 종이 QR 기간 재확인(`period_check`)도 현황 값을 바꾸지 않아 보내지 않는다.
- 각 인스턴스가 한 DB 연결로 같은 채널을 LISTEN 한다. 스캔을 처리한 인스턴스와 구독 인스턴스가 달라도 전달된다. 이 연결은 Hikari 풀(`DB_POOL_SIZE`)에서 하나를 계속 쓴다.
- 스캔 트랜잭션은 NOTIFY만 남기고 끝난다. 브라우저 전송은 공용 송신 스레드에서 하므로 스캔이 느린 수신자를 기다리지 않는다.

## 연결 관리

공통 `global/sse/SseConnectionRegistry`(Application과 같은 구현)가 연결 등록·해제, heartbeat, 전송 실패·수명 만료 정리를 맡고 도메인은 신호만 넘긴다.

- 요청 스레드를 점유하지 않는다. 전송은 공용 송신 스레드(2개)에서 한다. 송신 큐는 연결 수 한도를 넘지 않는다.
- 전송 실패, 클라이언트 종료, 수명 만료 때 연결을 정리한다.
- 권한: 연결 수명(기본 30분)이 끝나면 서버가 닫는다. 재연결 때 게이트웨이가 JWT와 역할을 다시 확인하므로 권한 회수나 토큰 만료는 최대 연결 수명 안에 반영된다.

| 설정 | 기본값 |
| --- | --- |
| `ATTENDANCE_ADMIN_EVENTS_ENABLED` | `false` |
| `ATTENDANCE_ADMIN_EVENTS_MAX_CONNECTIONS` | `500` (인스턴스당) |
| `ATTENDANCE_ADMIN_EVENTS_MAX_CONNECTIONS_PER_EXPO` | `50` (인스턴스당) |
| `ATTENDANCE_ADMIN_EVENTS_CONNECTION_LIFETIME` | `PT30M` |
| `ATTENDANCE_ADMIN_EVENTS_HEARTBEAT_INTERVAL` | `PT25S` |
| `ATTENDANCE_ADMIN_EVENTS_COALESCE_INTERVAL` | `PT1S` (`PT0S`면 모으지 않음) |

꺼져 있으면 구독 경로가 없고(`404`) LISTEN 연결도 만들지 않는다. 트리거는 항상 신호를 보내지만 듣는 연결이 없으면 버려진다.

## 운영 전 확인

- Gateway가 `/attendance/**`를 지금처럼 프록시하며 `text/event-stream`을 버퍼링하지 않고 바로 흘려보내는지, 응답 timeout이 연결 수명(30분)보다 길거나 SSE에서 꺼져 있는지 확인한다. `X-User-Role` 전달은 기존 현황 GET과 같다.
- DB 앞에 트랜잭션 단위 커넥션 풀러(예: PgBouncer transaction mode)가 있으면 LISTEN이 동작하지 않는다.
- Client는 연결·재연결 직후 GET 재조회와 이벤트 수신 시 재조회를 구현한다.
