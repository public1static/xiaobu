ALTER TABLE ledger_accounts
    ADD COLUMN opening_balance_cents BIGINT NOT NULL DEFAULT 0;

CREATE TABLE ledger_account_transfers (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    source_account_id VARCHAR(48) NOT NULL,
    target_account_id VARCHAR(48) NOT NULL,
    amount_cents BIGINT NOT NULL,
    booked_on DATE NOT NULL,
    note VARCHAR(100) NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_account_transfer_history (ledger_id, booked_on, id),
    CONSTRAINT fk_transfer_source FOREIGN KEY (ledger_id, source_account_id)
        REFERENCES ledger_accounts (ledger_id, id),
    CONSTRAINT fk_transfer_target FOREIGN KEY (ledger_id, target_account_id)
        REFERENCES ledger_accounts (ledger_id, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
