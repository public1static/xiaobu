CREATE TABLE ledger_categories (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    code VARCHAR(20) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    name VARCHAR(24) NOT NULL,
    display_order INT NOT NULL,
    active TINYINT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_ledger_code (ledger_id, code),
    UNIQUE KEY uk_category_ledger_direction_name (ledger_id, direction, name),
    CONSTRAINT fk_category_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
