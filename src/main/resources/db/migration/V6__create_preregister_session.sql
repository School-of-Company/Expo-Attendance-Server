-- 사전등록 참가자가 신청한 회차. 신청 서비스가 확정·승급 때 기록하고 취소 때 무효로 바꾼다.
-- 행이 없는 참가자(현장등록, 신청 서비스 연동 전 등록)는 회차 확인 없이 기존 방식으로 입장한다.
CREATE TABLE tb_preregister_session
(
    id             BIGSERIAL PRIMARY KEY,
    expo_id        VARCHAR(36) NOT NULL,
    participant_id BIGINT      NOT NULL,
    session_id     BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL,
    updated_at     TIMESTAMP   NOT NULL,
    CONSTRAINT uk_preregister_session_participant UNIQUE (expo_id, participant_id)
);
