-- 간편입장 request_id 중복 판정을 박람회별로 맞춘다(#169). 확인(existsRequest)은 박람회별인데 유니크가 전역이라,
-- 다른 박람회에 같은 값이 오면 insert가 조용히 버려져 집계되지 않았다. 전역 유니크는 박람회별 유니크를 포함하므로 기존 데이터에서 실패하지 않는다.
-- request_id가 NULL인 행은 유니크 대상이 아니라 여러 번 들어갈 수 있다.
ALTER TABLE tb_walk_in_log DROP CONSTRAINT uk_walk_in_request;
ALTER TABLE tb_walk_in_log ADD CONSTRAINT uk_walk_in_expo_request UNIQUE (expo_id, request_id);
