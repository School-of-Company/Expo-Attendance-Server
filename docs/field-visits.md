# 날짜별 FIELD 권위 (#204)

Application #311은 등록 ID(UUID), 참가자, KST 방문일, generation을 소유한다.
Attendance의 `PUT /internal/expos/{expoId}/participants/{participantId}/field-visits/{registrationId}`는
`{visitDate,generation,status,operationId}`를 projection하고 204를 반환한다.
같은 세대 CANCELLED는 최종 상태이며 이전 세대 요청은 변경 없이 204다.
등록 ID의 참가자·박람회·방문일은 바꿀 수 없다. PRE와 다른 방문일은 보존한다.
QR 발급이나 SMS 호출을 추가하지 않으며 기존 participantId/QR을 사용한다.

입장·재입장·참가자 프로그램 스캔에는 오늘 ACTIVE FIELD 또는 오늘 ACTIVE PRE가 필요하다.
PRE QR은 기존 회차 시간 창을 적용하고 전화번호는 시간 창 대신 방문 날짜를 확인한다.
PRE 전화번호 입장은 시간검사 없이 오늘 ACTIVE 회차 중 시작이 가장 이른 ID를
preregisterSessionId로 전달한다(시작이 같으면 작은 ID). FIELD 원본 프로필도 PRE 자격을 유지한다.
날짜 미상 FIELD, 과거 `FIELD_REGISTERED`, 생성일로 오늘 권한을 만들지 않는다.

## 날짜별 취소 계약

모든 경로는 위 prefix 아래이며 내부 토큰이 필요하다.

| 메서드 / 경로 | JSON body | 응답 |
| --- | --- | --- |
| POST `/cancellation-status` | `{phoneNumber,visitDate,generation}` | 200 대상 echo + `enteredOnVisitDate,programAttended,guardState,linkedTraineeId` |
| PUT `/cancellation-guards/{operationId}` | 동일 | 204 |
| POST `/cancellation-guards/{operationId}/commit` | `{visitDate,generation}` | 204 |
| DELETE `/cancellation-guards/{operationId}` | 동일 | 204 |

상태 응답은 registrationId/participantId/visitDate/generation을 정확히 echo한다.
guardState는 NONE/HELD/COMMITTED이며 abort는 NONE이다.
User 기존 cancellation-status는 소유 확인에만 쓰고 누적 entered는 무시한다.
첫입장은 대상 날짜 User entry-status, 독립 fence, 기존 entry log로 판정한다.
퇴장해도 취소 자격이 되살아나지 않으며 전날 입장은 영향을 주지 않는다.
출석은 대상 날짜의 일반 프로그램 및 연결 연수자 출석을 확인한다.

acquire는 박람회 → 참가자 → 등록 ID → 연결 연수자 순으로 잠그며 진행 중인 스캔을 기다린다.
HELD는 그 날짜의 참가자와 연결 연수자 스캔을 막는다.
commit은 해당 FIELD만 취소하며 다른 유효 PRE/FIELD는 유지한다.
Application은 acquire → 로컬 결정 → commit → CANCELLED projection 순서를 지킨다.
abort tombstone은 늦은 acquire를 409로 막는다. 이전 세대 acquire/commit/abort는 204다.

## User entry 연계와 응답 유실

STANDARD `POST /internal/entries`에는 고정한 KST `attendanceDate`를 보낸다.
FIELD에는 `fieldVisitRegistrationId,fieldVisitGeneration`도 보내며 PRE 회차와 혼합하지 않는다.
User는 ACTIVE projection과 일치하는지 확인하고 프로필/QR을 변경하지 않는다.
`POST /internal/standard-participants/entry-status`는 `{expoId,participantId,attendanceDate}`를 받고
`{participantId,attendanceDate,entered}`를 반환한다. echo 불일치·미지원·404·오류는 503이다.

STANDARD 스캔은 User 호출 전 REQUIRES_NEW로 `tb_entry_scan_fence`에 시도를 저장한다.
200은 entered로 확정하고 명확한 400/404는 그 시도만 해제한다.
409는 중복 입장 외에 권위 불일치도 뜻한다. 정확한 날짜·참가자 echo의 entry-status가
entered=true일 때만 확정하며 false·조회 실패·echo 불일치는 PENDING과 503을 유지한다.
503/timeout/crash는 PENDING을 보존한다. 원격 false는 late commit을 배제하지 못하므로
PENDING이 있는 취소 조회/acquire는 503이다. 이후 성공/409 또는 실제 날짜별 입장이
확인되면 취소 불가로 판정한다. 시간 경과로 fence를 해제하지 않는다.
끝내 결과가 불확실한 시도는 운영 확인이 필요하며 자동 만료는 없다.
독립 fence를 쓸 때 공유 락을 보유한 스캔은 DB 연결을 하나 더 사용한다.

## 배포와 검증

1. Application V41 원장을 배포하고 field-visits 플래그는 비활성으로 둔다.
2. User #202 및 Attendance V24/계약을 배포하고 알려진 방문일 projection을 전달한다.
   날짜 미상 legacy FIELD는 차단하며 추정 backfill하지 않는다.
3. 등록→입장, 취소→재등록, 응답 유실, 옛 세대 지연, KST 자정, PRE 보존을 실통합 검증한다.
4. Application field-visits 활성화 후 #310 V42 취소 소비를 활성화한다.

새 필수 환경변수는 없다. Docker가 없으면 `FieldVisitPersistenceTests`의 PostgreSQL SQL,
독립 트랜잭션 및 실제 advisory lock 경합은 미검증이다. fakeprovider/MockK는 실통합을 대신하지 않는다.

## 로컬 검증 결과 (2026-10-22)

- `test --tests '*Spec'`: 292 passed, 0 failed/skipped.
- 새 FIELD 전용 spec: projection/guard 23, 날짜 자격 11, HTTP API 6, 자정 스캔 1,
  User HTTP fakeprovider 1. 기존 ScanEntry spec에도 409 false/echo 불일치/조회 실패,
  PRE 전화번호 회차 전달과 PENDING 보존 회귀를 추가했다.
- `ktlintCheck spotlessCheck assemble` 통과. 전체 테스트 소스 컴파일 통과.
- `FieldVisitPersistenceTests` 2개 및 기존 Testcontainers 통합 테스트는 Docker 부재로 미실행.
  migration, 실제 advisory lock 경합, 독립 fence commit/rollback, 서비스 간 실통합은 미검증이다.
- 스캔 시작 날짜를 guard·자격·fence·User·STANDARD 집계 로그에 동일하게 고정한다.
- 운영 DB, 실제 SMS, 배포, 커밋, push, PR, merge는 실행하지 않았다.

모든 Gradle 호출은 부모 Perl `Fcntl LOCK_EX`가 `/tmp/expo-application-gradle.lock`을
자식 종료까지 유지하며 직렬 실행했다. `nice -n 10`, `--no-daemon --max-workers=1 --no-parallel`,
in-process Kotlin, Gradle heap 1024m/CPU 2를 적용했다. 임시 init script는 Test fork 1,
heap 512m/CPU 2/context cache 4로 제한했다. 최종 실행 task는 다음과 같다.

```text
./gradlew ktlintFormat test --tests '*Spec' ktlintCheck spotlessCheck assemble
```

PR 준비 시 커밋 전 `ktlintCheck build`도 같은 잠금·자원 제한 아래 실행하였다.
292개 회귀는 통과하였고 `contextLoads()` 1개가 `DockerClientProviderStrategy`에서
Docker 환경을 찾지 못해 실패하였다. 첫 실패 중단을 사용하였으며 전체 build 성공으로 보고하지 않는다.
