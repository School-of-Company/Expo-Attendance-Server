-- #149: 간편입장 구분에 참가 유형 8종을 추가한다. 가장 긴 값(MIDDLE_SCHOOL_STUDENT, 21자)이 VARCHAR(20)에 들어가지 않아 넓힌다.
-- 기존 값(ADULT, STUDENT)과 행은 그대로 둔다. 길이만 늘리므로 테이블을 다시 쓰지 않는다.
ALTER TABLE tb_walk_in_log ALTER COLUMN category TYPE VARCHAR(30);
