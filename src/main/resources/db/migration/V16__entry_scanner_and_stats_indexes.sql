-- 실시간 입·퇴장 현황(#131). 입구 스캐너(기기)가 보낸 이름(`X-Scanner-Name`)을 입장·퇴장·간편입장에 함께 남겨 스캐너별 처리 건수를 센다.
-- 보내지 않은 요청과 이 변경 이전의 기록은 비어 있다. 최근 10분·시간대별·최근 기록 조회를 위해 시각 인덱스를 둔다.
ALTER TABLE tb_entry_log
    ADD COLUMN scanner      VARCHAR(40),
    ADD COLUMN exit_scanner VARCHAR(40);

ALTER TABLE tb_walk_in_log
    ADD COLUMN scanner VARCHAR(40);

CREATE INDEX ix_entry_log_expo_entered ON tb_entry_log (expo_id, entered_at);
CREATE INDEX ix_walk_in_expo_created ON tb_walk_in_log (expo_id, created_at);
