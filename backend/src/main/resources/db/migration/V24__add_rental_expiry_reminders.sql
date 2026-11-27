ALTER TABLE rental_contracts
    ADD COLUMN remind_days INT NOT NULL DEFAULT 30,
    ADD COLUMN handled_end DATE,
    ADD KEY ix_rental_contract_expiry (ledger_id, deleted, ends_on, id);
