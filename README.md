Expo-Attention-Server

[ 스타트업 박람회 프로젝트 | Server-V2 ] 참여 서비스 — 박람회 입장 스캔, 프로그램 출석, 현장 종이 QR 도메인을 담당한다.

## 환경 변수

로컬 값은 `.env.example`을 복사해 `.env`로 만들어 채운다. 토큰은 서버 간 호출 인증용이라 32자 이상이어야 하며, 짧으면 기동이 실패한다.

| 변수 | 설명 |
| --- | --- |
| `INTERNAL_TOKEN` | 이 서비스의 `/internal/**`를 보호하는 `X-Internal-Token` 값. 폼 서비스가 이 값을 `PARTICIPATION_SERVICE_INTERNAL_TOKEN`으로 들고 있다 |
| `USER_INTERNAL_TOKEN`, `EXPO_INTERNAL_TOKEN`, `APPLICATION_INTERNAL_TOKEN` | 유저·박람회·신청 서비스를 호출할 때 보내는 토큰. 각 서비스의 `INTERNAL_TOKEN`과 같은 값 |
| `USER_URL`, `EXPO_URL`, `APPLICATION_URL` | 비우면 Eureka로 찾는다. Gateway·Eureka 없이 로컬에서 직접 부를 때만 지정한다 |
| `KAFKA_BOOTSTRAP_SERVERS` | 입장 이벤트를 발행하는 브로커 (기본 `localhost:19092`) |
| `KAFKA_ENTRY_RECORDED_TOPIC` | 입장 이벤트 토픽 (기본 `attention.entry.recorded`) |

`/internal`은 게이트웨이 라우팅에 넣지 않는다. 게이트웨이가 모르는 경로는 404로 막아 주기 때문에 외부에서 닿지 않는다.
