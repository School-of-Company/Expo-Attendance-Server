-- 설문 문자는 받는 번호 기준으로 하루 한 통만 남긴다. 동행자는 번호가 없고 대표자 번호로 문자를 받으므로,
-- 참가자 단위 유니크(expo_id, participant_id, attendance_date)면 한 번호로 최대 인원 수만큼 문자가 간다.
ALTER TABLE tb_entry_outbox
    DROP CONSTRAINT uk_entry_outbox_participant_date;

ALTER TABLE tb_entry_outbox
    ADD CONSTRAINT uk_entry_outbox_phone_date UNIQUE (expo_id, phone_number, attendance_date);
