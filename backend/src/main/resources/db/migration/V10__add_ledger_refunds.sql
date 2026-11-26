ALTER TABLE ledger_transactions ADD UNIQUE KEY uk_transaction_ledger_id (ledger_id, id);

CREATE TABLE ledger_refunds (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    transaction_id VARCHAR(48) NOT NULL,
    amount_cents BIGINT NOT NULL,
    received_on DATE NOT NULL,
    account_id VARCHAR(48) NULL,
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_refund_ledger_date (ledger_id, received_on),
    KEY ix_refund_original (ledger_id, transaction_id, received_on),
    CONSTRAINT fk_refund_original FOREIGN KEY (ledger_id, transaction_id)
        REFERENCES ledger_transactions (ledger_id, id),
    CONSTRAINT fk_refund_account FOREIGN KEY (ledger_id, account_id)
        REFERENCES ledger_accounts (ledger_id, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
