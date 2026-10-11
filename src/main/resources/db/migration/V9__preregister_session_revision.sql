-- 신청 서비스가 보내는 참가자별 revision. 더 오래된 PUT·DELETE가 늦게 도착해도 상태를 바꾸지 않게 한다.
-- 0은 revision 없이 보낸 요청만 있었다는 뜻이다(첫 revision은 항상 더 새롭다).
ALTER TABLE tb_preregister_session
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

-- 확정 기록이 없는 참가자의 취소(revision 포함)도 남겨, 그 뒤에 늦게 도착한 오래된 확정이 취소를 되돌리지 못하게 한다.
ALTER TABLE tb_preregister_session
    ALTER COLUMN session_id DROP NOT NULL;
