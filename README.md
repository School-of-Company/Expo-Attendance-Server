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

## 헬스 체크

| 경로 | 내용 |
| --- | --- |
| `/actuator/health` | 배포 헬스체크 |
| `/actuator/health/liveness` | 프로세스 상태 |
| `/actuator/health/readiness` | 준비 상태와 DB 연결 |
