-- commit이 보호를 확정하지 않고 해제로 끝낸 작업인지 구분한다(#168). 해제로 끝난 작업의 commit 재시도는 204로 답한다.
-- 기존 행은 모두 abort로 끝난 작업이므로 FALSE로 둔다.
ALTER TABLE tb_cancellation_aborted
    ADD COLUMN released_by_commit BOOLEAN NOT NULL DEFAULT FALSE;
