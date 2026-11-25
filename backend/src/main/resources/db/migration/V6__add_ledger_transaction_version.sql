ALTER TABLE ledger_transactions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
