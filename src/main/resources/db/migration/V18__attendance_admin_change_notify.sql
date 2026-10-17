-- #141: 실시간 입·퇴장 현황 갱신 신호. 현황 GET이 읽는 tb_entry_log, tb_walk_in_log의 실제 변화만 박람회 ID로 NOTIFY 한다.
-- NOTIFY는 커밋된 트랜잭션만 전달되고 롤백되면 버려진다. 같은 트랜잭션의 같은 payload는 한 번만 전달된다.
-- 모든 인스턴스가 같은 DB 채널을 LISTEN 하므로 스캔을 처리한 인스턴스와 구독 인스턴스가 달라도 전달된다.
-- 중복 스캔(ON CONFLICT DO NOTHING으로 행이 생기지 않음)과 실패한 스캔은 행이 바뀌지 않아 신호를 만들지 않는다.
CREATE FUNCTION notify_attendance_admin_change() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_notify(
        'attendance_admin_changes',
        (CASE WHEN TG_OP = 'DELETE' THEN OLD.expo_id ELSE NEW.expo_id END)::TEXT
    );
    RETURN NULL;
END
$$;

-- 입장 로그: 등록 QR(일반 참가자·연수자)과 종이 QR 입장, 퇴장, 박람회 삭제 정리
CREATE TRIGGER tr_entry_log_admin_change
    AFTER INSERT OR DELETE ON tb_entry_log
    FOR EACH ROW EXECUTE FUNCTION notify_attendance_admin_change();
CREATE TRIGGER tr_entry_log_admin_exit
    AFTER UPDATE OF exited_at ON tb_entry_log
    FOR EACH ROW WHEN (OLD.exited_at IS DISTINCT FROM NEW.exited_at)
    EXECUTE FUNCTION notify_attendance_admin_change();

-- 간편입장: 추가와 취소(-1 행), 박람회 삭제 정리. 같은 request_id 재전송은 행이 생기지 않는다
CREATE TRIGGER tr_walk_in_log_admin_change
    AFTER INSERT OR DELETE ON tb_walk_in_log
    FOR EACH ROW EXECUTE FUNCTION notify_attendance_admin_change();
