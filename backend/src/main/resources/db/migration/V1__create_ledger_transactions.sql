CREATE TABLE ledger_transactions (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount_cents BIGINT NOT NULL,
    booked_on DATE NOT NULL,
    category_code VARCHAR(20) NOT NULL,
    note VARCHAR(100) NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_ledger_date (ledger_id, direction, deleted, booked_on, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
