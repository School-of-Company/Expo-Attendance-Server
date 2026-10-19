-- #148: 입장·재입장·퇴장 이력. 사람(QR)이 하루에 여러 번 들어오고 나가도 시간순으로 남긴다.
-- tb_entry_log는 하루 최초 입장 한 행이고, exited_at은 "지금 퇴장 상태일 때만" 채워진다(재입장하면 비운다).
-- 퇴장 이력은 이 표가 가진다. 이 표 도입 전의 입장·퇴장은 tb_entry_log에서 옮겨 둔다.
CREATE TABLE tb_entry_event
(
    id              BIGSERIAL PRIMARY KEY,
    expo_id         VARCHAR(36) NOT NULL,
    subject_type    VARCHAR(20) NOT NULL,
    subject_id      VARCHAR(64) NOT NULL,
    attendance_date DATE        NOT NULL,
    event_type      VARCHAR(10) NOT NULL,
    occurred_at     TIMESTAMP   NOT NULL,
    scanner         VARCHAR(40)
);

CREATE INDEX ix_entry_event_expo_day ON tb_entry_event (expo_id, attendance_date, occurred_at);
CREATE INDEX ix_entry_event_subject ON tb_entry_event (expo_id, subject_type, subject_id, attendance_date);

INSERT INTO tb_entry_event (expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner)
SELECT expo_id, subject_type, subject_id, attendance_date, 'ENTER', entered_at, scanner
FROM tb_entry_log;

INSERT INTO tb_entry_event (expo_id, subject_type, subject_id, attendance_date, event_type, occurred_at, scanner)
SELECT expo_id, subject_type, subject_id, attendance_date, 'EXIT', exited_at, exit_scanner
FROM tb_entry_log
WHERE exited_at IS NOT NULL;
