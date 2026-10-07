-- 프로그램 삭제 기록. 삭제 뒤에 도착한 스캔이 지운 출석을 되살리지 못하게 막는다.
-- program_key는 `STANDARD:<id>` 또는 `TRAINING:<id>`다(일반·연수 프로그램의 ID 공간이 서로 다르므로).
CREATE TABLE tb_deleted_program
(
    program_key VARCHAR(40) PRIMARY KEY,
    deleted_at  TIMESTAMP NOT NULL
);
