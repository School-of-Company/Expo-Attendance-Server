# Expo-Attention-Server

[ 스타트업 박람회 프로젝트 | Server-V2 ] 참여 서비스. 박람회 입장·퇴장 스캔, 프로그램 출석, 현장 종이 QR을 담당한다.

규칙은 `AGENTS.md`, 서비스 간 계약은 `docs/service-contracts.md`에 있다.

## 실행

```bash
cp .env.example .env          # 값을 채운다. 토큰은 32자 이상
docker compose up -d          # 로컬 PostgreSQL (127.0.0.1:15434)
set -a; . ./.env; set +a
./gradlew bootRun             # 스키마는 Flyway가 맞춘다
```

## 스캔 권한과 박람회 서비스 장애

스캔·현장 집계 API는 받은 `Authorization`으로 박람회 서비스의 `admin-access` 판정을 먼저 확인한다. 헤더가 없거나 비어 있으면 401, 권한 거부는 403, 판정 서비스 장애·회로 차단은 503이며 읽기·쓰기를 진행하지 않는다. 게이트웨이는 원래 `Authorization`을 하위 서비스에 전달해야 한다.

종이 QR도 권한 판정에 성공해야 입장할 수 있다. 권한 확인 후 기간 조회만 실패한 경우에 한해 `period_check = PENDING`으로 기록하고 나중에 재확인한다. 박람회 서비스 전체 장애 시에는 이 대체 경로를 사용하지 않는다. 배포 전 게이트웨이를 경유한 헤더 전달과 박람회 권한 판정 지원을 확인한다.

## 헬스 체크

| 경로 | 내용 |
| --- | --- |
| `/actuator/health` | 배포 헬스체크 |
| `/actuator/health/liveness` | 프로세스 상태 |
| `/actuator/health/readiness` | 준비 상태와 DB 연결 |
