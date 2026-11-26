ALTER TABLE fixed_bills
    ADD COLUMN stop_month DATE,
    ADD KEY ix_fixed_bill_open_plan (ledger_id, deleted, stop_month, id);
