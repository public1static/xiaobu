CREATE TABLE fixed_bills (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    name VARCHAR(40) NOT NULL,
    first_due_on DATE NOT NULL,
    amount_cents BIGINT NOT NULL,
    category_code VARCHAR(48) NOT NULL,
    account_id VARCHAR(48),
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_fixed_bill_ledger_date (ledger_id, deleted, first_due_on, id),
    FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE fixed_bill_payments (
    id VARCHAR(48) NOT NULL,
    bill_id VARCHAR(48) NOT NULL,
    period_start DATE NOT NULL,
    transaction_id VARCHAR(48) NOT NULL,
    paid_on DATE NOT NULL,
    amount_cents BIGINT NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fixed_bill_period (bill_id, period_start),
    UNIQUE KEY uk_fixed_bill_transaction (transaction_id),
    FOREIGN KEY (bill_id) REFERENCES fixed_bills (id),
    FOREIGN KEY (transaction_id) REFERENCES ledger_transactions (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
