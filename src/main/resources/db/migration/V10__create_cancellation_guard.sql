-- 신청 서비스가 참가자 신청을 취소하는 동안 입장·프로그램 스캔을 막는 취소 보호(#79).
-- HELD: 취소를 검사하고 확정하는 중. COMMITTED: 취소가 확정됨(재신청으로 회차가 다시 기록되면 지운다).
-- 시간으로 자동 해제하지 않는다. 신청 서비스가 commit 또는 abort를 보낼 때까지 유지한다.
CREATE TABLE tb_cancellation_guard
(
    id             BIGSERIAL PRIMARY KEY,
    expo_id        VARCHAR(36) NOT NULL,
    participant_id BIGINT      NOT NULL,
    trainee_id     BIGINT,
    operation_id   VARCHAR(64) NOT NULL,
    state          VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL,
    CONSTRAINT uk_cancellation_guard_participant UNIQUE (expo_id, participant_id)
);

CREATE INDEX idx_cancellation_guard_trainee ON tb_cancellation_guard (expo_id, trainee_id) WHERE trainee_id IS NOT NULL;
