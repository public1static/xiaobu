CREATE TABLE ledger_manual_reminders (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    title VARCHAR(60) NOT NULL,
    due_on DATE NOT NULL,
    remind_at DATETIME(0) NOT NULL,
    completed BIT(1) NOT NULL DEFAULT b'0',
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_manual_reminder_todo (ledger_id, created_by, deleted, completed, remind_at),
    CONSTRAINT fk_ledger_manual_reminder_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
