ALTER TABLE fixed_bills
    ADD COLUMN cycle_months INT NOT NULL DEFAULT 1,
    ADD COLUMN last_due_on DATE;

CREATE TABLE rental_homes (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    name VARCHAR(40) NOT NULL,
    address VARCHAR(120) NOT NULL,
    note VARCHAR(100) NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY ix_rental_home_page (ledger_id, deleted, id),
    CONSTRAINT fk_rental_home_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE rental_contracts (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    home_id VARCHAR(48) NOT NULL,
    landlord_name VARCHAR(40) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    monthly_rent_cents BIGINT NOT NULL,
    deposit_cents BIGINT NOT NULL,
    cycle VARCHAR(16) NOT NULL,
    bill_id VARCHAR(48),
    note VARCHAR(100) NOT NULL,
    created_by VARCHAR(48) NOT NULL,
    updated_by VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rental_contract_bill (bill_id),
    KEY ix_rental_contract_page (ledger_id, deleted, starts_on, id),
    KEY ix_rental_home_contract_page (ledger_id, home_id, deleted, starts_on, id),
    CONSTRAINT fk_rental_contract_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id),
    CONSTRAINT fk_rental_contract_home FOREIGN KEY (home_id) REFERENCES rental_homes (id),
    CONSTRAINT fk_rental_contract_bill FOREIGN KEY (bill_id) REFERENCES fixed_bills (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
