-- 입장 기록 목록(entry-records, #250)을 (날짜, 시각, id) 키셋으로 넘기기 위한 인덱스. 정렬 끝에 id가 있어 같은 시각 행도 인덱스 순서로 읽고,
-- 페이지마다 범위 전체를 정렬하지 않는다. 기존 ix_entry_event_expo_day는 이 인덱스의 앞부분이라 그 인덱스를 쓰던 조회도 그대로 탄다.
-- 바꾸는 것이라 이벤트 INSERT의 인덱스 갱신 수는 늘지 않는다.
CREATE INDEX ix_entry_event_expo_day_id ON tb_entry_event (expo_id, attendance_date, occurred_at, id);
DROP INDEX ix_entry_event_expo_day;
