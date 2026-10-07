-- 박람회 삭제 기록. 삭제 뒤에 도착한 발급 요청이 지운 데이터를 되살리지 못하게 막는다.
CREATE TABLE tb_deleted_expo
(
    expo_id    VARCHAR(36) PRIMARY KEY,
    deleted_at TIMESTAMP NOT NULL
);
