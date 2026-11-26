CREATE TABLE gift_reasons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ledger_id VARCHAR(48) NOT NULL,
    name VARCHAR(60) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gift_reason_order (ledger_id, display_order),
    UNIQUE KEY uk_gift_reason_name (ledger_id, name),
    CONSTRAINT fk_gift_reason_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
