ALTER TABLE rental_contracts
    ADD COLUMN previous_contract_id VARCHAR(48),
    ADD UNIQUE KEY uk_rental_contract_previous (previous_contract_id),
    ADD CONSTRAINT fk_rental_contract_previous FOREIGN KEY (previous_contract_id) REFERENCES rental_contracts (id);
