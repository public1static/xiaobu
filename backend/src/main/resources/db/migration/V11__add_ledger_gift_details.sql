CREATE TABLE ledger_gift_details (
    transaction_id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    book_code VARCHAR(24) NOT NULL,
    person_name VARCHAR(60) NULL,
    reason VARCHAR(60) NULL,
    deleted TINYINT(1) NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (transaction_id),
    KEY ix_gift_ledger_book (ledger_id, book_code),
    CONSTRAINT fk_gift_transaction FOREIGN KEY (ledger_id, transaction_id)
        REFERENCES ledger_transactions (ledger_id, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
