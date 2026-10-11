-- 중단(abort)된 취소 작업의 operationId. 중단된 뒤 지연되어 도착한 같은 operationId의 acquire가 보호를 다시 걸지 못하게 한다(#88).
-- 취소 작업은 참가자당 한 번의 시도라 행이 적게 쌓이고, 박람회를 삭제할 때 함께 지운다.
CREATE TABLE tb_cancellation_aborted
(
    expo_id        VARCHAR(36) NOT NULL,
    participant_id BIGINT      NOT NULL,
    operation_id   VARCHAR(64) NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    PRIMARY KEY (expo_id, participant_id, operation_id)
);
