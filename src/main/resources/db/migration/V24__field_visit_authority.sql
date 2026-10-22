CREATE TABLE tb_field_visit (
    registration_id UUID PRIMARY KEY,
    expo_id VARCHAR(36) NOT NULL,
    participant_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    generation BIGINT NOT NULL CHECK (generation > 0),
    status VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    operation_id UUID NOT NULL
);
CREATE INDEX ix_field_visit_participant_day ON tb_field_visit (expo_id, participant_id, visit_date);

CREATE TABLE tb_field_visit_guard (
    registration_id UUID NOT NULL,
    generation BIGINT NOT NULL CHECK (generation > 0),
    operation_id UUID NOT NULL,
    expo_id VARCHAR(36) NOT NULL,
    participant_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    trainee_id BIGINT,
    state VARCHAR(12) NOT NULL CHECK (state IN ('HELD', 'COMMITTED', 'ABORTED')),
    PRIMARY KEY (registration_id, generation, operation_id)
);
CREATE INDEX ix_field_visit_guard_participant ON tb_field_visit_guard (expo_id, participant_id, visit_date);
CREATE INDEX ix_field_visit_guard_trainee ON tb_field_visit_guard (expo_id, trainee_id, visit_date);

-- Kept independently of the remote call transaction: a lost response must never reopen cancellation.
CREATE TABLE tb_entry_scan_fence (
    attempt_id UUID PRIMARY KEY,
    expo_id VARCHAR(36) NOT NULL,
    participant_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    entered BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_entry_scan_fence_day ON tb_entry_scan_fence (expo_id, participant_id, attendance_date);
