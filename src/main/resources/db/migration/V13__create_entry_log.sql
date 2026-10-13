-- 입장 로그(#120). 입장을 기록할 때 같이 남겨 퇴장 기록과 입장 집계의 바탕으로 쓴다. 입장 자체의 기록은 그대로 유저 서비스(참가자·연수자)와
-- tb_qr_entry(종이 QR)에 있고, 이 표는 그 사본이라 배포 이전의 입장은 없다. 한 사람(QR)이 같은 날 한 번만 입장하므로 하루에 한 행이다.
CREATE TABLE tb_entry_log
(
    id              BIGSERIAL PRIMARY KEY,
    expo_id         VARCHAR(36) NOT NULL,
    subject_type    VARCHAR(20) NOT NULL,
    subject_id      VARCHAR(64) NOT NULL,
    attendance_date DATE        NOT NULL,
    entered_at      TIMESTAMP   NOT NULL,
    exited_at       TIMESTAMP,
    category        VARCHAR(30),
    CONSTRAINT uk_entry_log_subject_day UNIQUE (expo_id, subject_type, subject_id, attendance_date)
);

CREATE INDEX ix_entry_log_expo_day ON tb_entry_log (expo_id, attendance_date);
