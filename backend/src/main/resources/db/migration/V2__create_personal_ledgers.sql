CREATE TABLE personal_ledgers (
    id VARCHAR(48) NOT NULL,
    owner_id VARCHAR(48) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_personal_ledger_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
