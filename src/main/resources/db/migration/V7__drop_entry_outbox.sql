-- 입장 뒤 설문 문자를 보내지 않기로 해 입장 이벤트(attention.entry.recorded)를 없앴다.
-- 아웃박스에 남은 행에는 전화번호가 들어 있으므로 테이블째 지운다.
DROP TABLE tb_entry_outbox;
