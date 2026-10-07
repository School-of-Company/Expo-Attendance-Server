CREATE TABLE qr_token
(
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(64) NOT NULL,
    expo_id    VARCHAR(64) NOT NULL,
    category   VARCHAR(30) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT uk_qr_token_token UNIQUE (token)
);

CREATE TABLE qr_entry
(
    id              BIGSERIAL PRIMARY KEY,
    qr_token_id     BIGINT    NOT NULL REFERENCES qr_token (id),
    attendance_date DATE      NOT NULL,
    entry_time      TIMESTAMP NOT NULL,
    CONSTRAINT uk_qr_entry_token_date UNIQUE (qr_token_id, attendance_date)
);
