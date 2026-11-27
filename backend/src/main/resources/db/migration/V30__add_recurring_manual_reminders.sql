ALTER TABLE ledger_manual_reminders
    ADD COLUMN frequency VARCHAR(12) NOT NULL DEFAULT 'ONCE',
    ADD COLUMN anchor_on DATE NULL,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN stopped BIT(1) NOT NULL DEFAULT b'0';

UPDATE ledger_manual_reminders SET anchor_on = due_on;
ALTER TABLE ledger_manual_reminders MODIFY COLUMN anchor_on DATE NOT NULL;

CREATE TABLE ledger_manual_reminder_occurrences (
    id VARCHAR(72) NOT NULL,
    reminder_id VARCHAR(48) NOT NULL,
    revision BIGINT NOT NULL,
    title VARCHAR(60) NOT NULL,
    due_on DATE NOT NULL,
    remind_at DATETIME(0) NOT NULL,
    frequency VARCHAR(12) NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_manual_reminder_occurrence (reminder_id, revision),
    CONSTRAINT fk_manual_reminder_occurrence FOREIGN KEY (reminder_id) REFERENCES ledger_manual_reminders (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

INSERT INTO ledger_manual_reminder_occurrences (
    id, reminder_id, revision, title, due_on, remind_at, frequency,
    created_by, updated_by, created_at, updated_at, deleted
)
SELECT CONCAT(id, ':0'), id, 0, title, due_on, remind_at, frequency,
       created_by, updated_by, created_at, updated_at, b'0'
FROM ledger_manual_reminders WHERE completed = b'1' AND deleted = b'0';

UPDATE ledger_manual_reminders SET revision = 1 WHERE completed = b'1' AND deleted = b'0';
