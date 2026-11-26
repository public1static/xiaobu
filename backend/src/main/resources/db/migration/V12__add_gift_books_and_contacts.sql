CREATE TABLE gift_books (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    name VARCHAR(60) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    default_date DATE NULL,
    default_reason VARCHAR(60) NULL,
    archived TINYINT(1) NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gift_book_scope (ledger_id, id),
    UNIQUE KEY uk_gift_book_name (ledger_id, direction, name),
    CONSTRAINT fk_gift_book_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE gift_contacts (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    name VARCHAR(60) NOT NULL,
    distinction VARCHAR(60) NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gift_contact_scope (ledger_id, id),
    UNIQUE KEY uk_gift_contact_identification (ledger_id, name, distinction),
    CONSTRAINT fk_gift_contact_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

ALTER TABLE ledger_gift_details
    ADD COLUMN book_id VARCHAR(48) NULL,
    ADD COLUMN contact_id VARCHAR(48) NULL,
    ADD COLUMN person_distinction VARCHAR(60) NULL,
    ADD KEY ix_gift_activity_book (ledger_id, book_id),
    ADD KEY ix_gift_contact (ledger_id, contact_id),
    ADD CONSTRAINT fk_gift_detail_book FOREIGN KEY (ledger_id, book_id)
        REFERENCES gift_books (ledger_id, id),
    ADD CONSTRAINT fk_gift_detail_contact FOREIGN KEY (ledger_id, contact_id)
        REFERENCES gift_contacts (ledger_id, id);
