CREATE TABLE ledger_accounts (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    name VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_ledger_name (ledger_id, name),
    UNIQUE KEY uk_account_ledger_id (ledger_id, id),
    CONSTRAINT fk_account_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

ALTER TABLE ledger_transactions
    ADD COLUMN account_id VARCHAR(48) NULL,
    ADD CONSTRAINT fk_transaction_ledger_account
        FOREIGN KEY (ledger_id, account_id) REFERENCES ledger_accounts (ledger_id, id);
