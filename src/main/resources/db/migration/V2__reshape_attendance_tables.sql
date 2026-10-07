-- 박람회 입장 기록은 유저 서비스가 소유한다 (v1 participation 테이블 2개 제거)
DROP TABLE tb_standard_participant_participation;
DROP TABLE tb_trainee_participation;

-- 프로그램 출석: status 삭제, 시간은 TIME, (참가자, 프로그램) 유니크
DROP TABLE tb_standard_program_user;
DROP TABLE tb_training_program_user;

CREATE TABLE tb_standard_program_attendance
(
    id                  BIGSERIAL PRIMARY KEY,
    standard_program_id BIGINT NOT NULL,
    participant_id      BIGINT NOT NULL,
    attendance_date     DATE   NOT NULL,
    entry_time          TIME   NOT NULL,
    leave_time          TIME,
    CONSTRAINT uk_standard_attendance_participant_program UNIQUE (participant_id, standard_program_id)
);
CREATE INDEX ix_standard_attendance_program ON tb_standard_program_attendance (standard_program_id);

CREATE TABLE tb_training_program_attendance
(
    id                  BIGSERIAL PRIMARY KEY,
    training_program_id BIGINT NOT NULL,
    trainee_id          BIGINT NOT NULL,
    attendance_date     DATE   NOT NULL,
    entry_time          TIME   NOT NULL,
    leave_time          TIME,
    CONSTRAINT uk_training_attendance_trainee_program UNIQUE (trainee_id, training_program_id)
);
CREATE INDEX ix_training_attendance_program ON tb_training_program_attendance (training_program_id);

-- 종이 QR: 토큰이 PK, 입장은 (토큰, 날짜)당 한 번
DROP TABLE tb_qr_entry;
DROP TABLE tb_qr_token;

CREATE TABLE tb_qr_token
(
    token      VARCHAR(64) PRIMARY KEY,
    expo_id    VARCHAR(36) NOT NULL,
    category   VARCHAR(30) NOT NULL,
    created_at TIMESTAMP   NOT NULL
);
CREATE INDEX ix_qr_token_expo ON tb_qr_token (expo_id);

CREATE TABLE tb_qr_entry
(
    id              BIGSERIAL PRIMARY KEY,
    token           VARCHAR(64) NOT NULL REFERENCES tb_qr_token (token) ON DELETE CASCADE,
    attendance_date DATE        NOT NULL,
    entered_at      TIMESTAMP   NOT NULL,
    CONSTRAINT uk_qr_entry_token_date UNIQUE (token, attendance_date)
);

-- 입장 이벤트 아웃박스 (일반 참가자 설문 문자용)
CREATE TABLE tb_entry_outbox
(
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID        NOT NULL UNIQUE,
    expo_id         VARCHAR(36) NOT NULL,
    participant_id  BIGINT      NOT NULL,
    phone_number    VARCHAR(15) NOT NULL,
    attendance_date DATE        NOT NULL,
    status          VARCHAR(10) NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    published_at    TIMESTAMP,
    CONSTRAINT uk_entry_outbox_participant_date UNIQUE (expo_id, participant_id, attendance_date)
);
CREATE INDEX ix_entry_outbox_status_created ON tb_entry_outbox (status, created_at);
