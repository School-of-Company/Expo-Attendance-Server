-- 박람회 입장 기록 (v1 tb_standard_participant_participation)
CREATE TABLE tb_standard_participant_participation
(
    id                      BIGSERIAL PRIMARY KEY,
    entry_time              TIMESTAMP   NOT NULL,
    attendance_date         DATE        NOT NULL,
    standard_participant_id BIGINT      NOT NULL,
    expo_id                 VARCHAR(64) NOT NULL,
    CONSTRAINT uk_standard_participant_participation UNIQUE (expo_id, standard_participant_id, attendance_date)
);

-- 박람회 입장 기록 (v1 tb_trainee_participation)
CREATE TABLE tb_trainee_participation
(
    id              BIGSERIAL PRIMARY KEY,
    entry_time      TIMESTAMP   NOT NULL,
    attendance_date DATE        NOT NULL,
    trainee_id      BIGINT      NOT NULL,
    expo_id         VARCHAR(64) NOT NULL,
    CONSTRAINT uk_trainee_participation UNIQUE (expo_id, trainee_id, attendance_date)
);

-- 일반 프로그램 입·퇴실 (v1 tb_standard_program_user)
CREATE TABLE tb_standard_program_user
(
    id                      BIGSERIAL PRIMARY KEY,
    status                  BOOLEAN     NOT NULL DEFAULT FALSE,
    entry_time              VARCHAR(20),
    leave_time              VARCHAR(20),
    attendance_date         DATE        NOT NULL,
    standard_program_id     BIGINT      NOT NULL,
    standard_participant_id BIGINT      NOT NULL
);

-- 연수 프로그램 입·퇴실 (v1 tb_training_program_user)
CREATE TABLE tb_training_program_user
(
    id                  BIGSERIAL PRIMARY KEY,
    status              BOOLEAN NOT NULL DEFAULT FALSE,
    entry_time          VARCHAR(20),
    leave_time          VARCHAR(20),
    attendance_date     DATE    NOT NULL,
    training_program_id BIGINT  NOT NULL,
    trainee_id          BIGINT  NOT NULL
);

-- 현장 종이 QR 토큰 (v2 신규)
CREATE TABLE tb_qr_token
(
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(64) NOT NULL,
    expo_id    VARCHAR(64) NOT NULL,
    category   VARCHAR(30) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT uk_qr_token_token UNIQUE (token)
);

-- 종이 QR 입장 기록 (v2 신규, 토큰당 하루 한 번)
CREATE TABLE tb_qr_entry
(
    id              BIGSERIAL PRIMARY KEY,
    entry_time      TIMESTAMP   NOT NULL,
    attendance_date DATE        NOT NULL,
    qr_token_id     BIGINT      NOT NULL REFERENCES tb_qr_token (id) ON DELETE CASCADE,
    expo_id         VARCHAR(64) NOT NULL,
    CONSTRAINT uk_qr_entry UNIQUE (expo_id, qr_token_id, attendance_date)
);
