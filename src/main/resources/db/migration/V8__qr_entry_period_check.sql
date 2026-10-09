-- 박람회 서비스가 응답하지 못해 기간을 확인하지 못한 채 기록한 종이 QR 입장은 PENDING으로 남기고, 나중에 기간을 다시 확인해
-- VERIFIED 또는 OUT_OF_PERIOD, EXPO_NOT_FOUND로 바꾼다. 기존 행은 모두 기간을 확인하고 기록한 것이다.
ALTER TABLE tb_qr_entry
    ADD COLUMN period_check VARCHAR(20) NOT NULL DEFAULT 'VERIFIED';

CREATE INDEX ix_qr_entry_period_pending ON tb_qr_entry (id) WHERE period_check = 'PENDING';
