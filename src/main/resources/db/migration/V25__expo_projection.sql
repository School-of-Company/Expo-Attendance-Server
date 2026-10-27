-- 박람회 서비스 변경 이벤트(expo.expo.changed)로 채우는 읽기 전용 복사본(#240). 이 서비스가 읽는 값만 둔다.
-- 박람회 하나에 머리 행 하나. tombstone이 와도 행을 남겨 version을 기억한다(늦게 온 옛 메시지로 되살리지 않는다).
CREATE TABLE tb_expo_projection (
    expo_id VARCHAR(64) PRIMARY KEY,
    version BIGINT NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    deleted BOOLEAN NOT NULL,
    deleting BOOLEAN NOT NULL,
    -- 박람회 서비스에 진행 중인 프로그램 변경이 있다(programChangePending). 그동안 박람회 서비스는 프로그램 조회를 409로 답한다
    program_change_pending BOOLEAN NOT NULL DEFAULT FALSE,
    -- 박람회 서비스가 주는 yyyy-MM-dd 문자열 그대로. 읽을 수 없는 값은 동기 조회와 같이 503으로 다룬다
    started_day VARCHAR(32),
    finished_day VARCHAR(32),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 일반 프로그램 입실 시간대(KST 지역 일시 문자열 그대로)
CREATE TABLE tb_expo_projection_standard_program (
    expo_id VARCHAR(64) NOT NULL REFERENCES tb_expo_projection (expo_id),
    program_id BIGINT NOT NULL,
    started_at VARCHAR(32),
    ended_at VARCHAR(32),
    PRIMARY KEY (expo_id, program_id)
);

-- 연수 프로그램은 존재만 확인한다
CREATE TABLE tb_expo_projection_training_program (
    expo_id VARCHAR(64) NOT NULL REFERENCES tb_expo_projection (expo_id),
    program_id BIGINT NOT NULL,
    PRIMARY KEY (expo_id, program_id)
);

-- 사전등록 회차 시간. 참가자별 확정 회차(tb_preregister_session, 신청 서비스가 넣는다)와는 다른 데이터다
CREATE TABLE tb_expo_projection_session (
    expo_id VARCHAR(64) NOT NULL REFERENCES tb_expo_projection (expo_id),
    session_id BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (expo_id, session_id)
);
