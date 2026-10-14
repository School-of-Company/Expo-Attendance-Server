-- 종이 QR에 사람이 읽는 연번 코드(예: ADT-0001-K7)를 붙이고(#132), 분실한 종이를 무효화할 수 있게 한다.
-- 연번은 박람회·구분별로 1부터 매기고 무효화해도 다시 쓰지 않는다. 이 변경 이전에 발급한 토큰은 serial·code가 비어 있어
-- 코드로 조회·무효화할 수 없다(입장은 그대로 된다).
ALTER TABLE tb_qr_token
    ADD COLUMN serial     INT,
    ADD COLUMN code       VARCHAR(20),
    ADD COLUMN revoked_at TIMESTAMP;

CREATE UNIQUE INDEX uk_qr_token_expo_category_serial ON tb_qr_token (expo_id, category, serial) WHERE serial IS NOT NULL;
CREATE UNIQUE INDEX uk_qr_token_expo_code ON tb_qr_token (expo_id, code) WHERE code IS NOT NULL;
