-- 현황 recent(#255)가 입장 로그를 (입장 날짜, 입장 시각) 역순으로 앞 몇 건만 읽도록 인덱스 끝에 entered_at을 붙인다.
-- 기존 ix_entry_log_expo_day (expo_id, attendance_date)는 이 인덱스의 앞부분이라 그 인덱스를 쓰던 조회도 그대로 탄다.
-- 바꾸는 것이라 입장 로그 INSERT의 인덱스 갱신 수는 늘지 않는다. V26은 #250(entry-records 키셋 인덱스)이 쓴다.
CREATE INDEX ix_entry_log_expo_day_entered ON tb_entry_log (expo_id, attendance_date, entered_at);
DROP INDEX ix_entry_log_expo_day;
