-- QR 없는 방문객의 간편입장(#118). 개인정보 없이 박람회·날짜·구분별 인원만 센다. 행을 더하는 기록(+1, 취소는 -1)으로 남겨 동시에 눌러도 유실이 없고,
-- 합계는 SUM으로 읽는다. request_id를 보내면 같은 요청의 재전송(응답 유실)이 두 번 세지지 않는다.
CREATE TABLE tb_walk_in_log
(
    id              BIGSERIAL PRIMARY KEY,
    expo_id         VARCHAR(36) NOT NULL,
    attendance_date DATE        NOT NULL,
    category        VARCHAR(20) NOT NULL,
    delta           SMALLINT    NOT NULL,
    request_id      VARCHAR(64),
    created_at      TIMESTAMP   NOT NULL,
    CONSTRAINT ck_walk_in_delta CHECK (delta IN (1, -1)),
    CONSTRAINT uk_walk_in_request UNIQUE (request_id)
);

CREATE INDEX ix_walk_in_expo_day ON tb_walk_in_log (expo_id, attendance_date, category);
