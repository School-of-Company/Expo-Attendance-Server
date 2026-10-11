-- 취소를 확정한 시점의 사전등록 회차 revision(신청 서비스의 DELETE preregister-session revision).
-- 취소보다 오래된 PUT이 확정된 취소 보호를 풀지 못하게 하고, 더 새로운 PUT이 먼저 도착한 취소는 확정 대신 해제한다(#92).
-- revision 없이 확정한 보호는 NULL이고 기존처럼 새 회차 PUT이 오면 풀린다.
ALTER TABLE tb_cancellation_guard
    ADD COLUMN cancel_revision BIGINT;
