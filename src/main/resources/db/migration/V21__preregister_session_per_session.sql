-- #160: 사전등록이 사람당 여러 회차가 된다. 참가자당 한 행이던 회차 기록을 (참가자, 회차)당 한 행으로 바꾼다.
-- 회차 없이 남긴 취소 기록(session_id NULL, 회차 지정 없는 취소)도 참가자당 하나만 두도록 NULL끼리 같은 값으로 본다.
-- 기존 행은 참가자당 하나였으므로 그대로 새 제약을 만족한다.
ALTER TABLE tb_preregister_session
    DROP CONSTRAINT uk_preregister_session_participant;

ALTER TABLE tb_preregister_session
    ADD CONSTRAINT uk_preregister_session_participant_session UNIQUE NULLS NOT DISTINCT (expo_id, participant_id, session_id);
