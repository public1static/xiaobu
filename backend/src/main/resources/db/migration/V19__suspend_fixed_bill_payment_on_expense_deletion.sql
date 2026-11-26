ALTER TABLE fixed_bill_payments
    ADD COLUMN awaiting_source_restore BOOLEAN NOT NULL DEFAULT FALSE,
    ADD KEY ix_fixed_bill_payment_source_restore (transaction_id, awaiting_source_restore);

UPDATE fixed_bill_payments p
    JOIN ledger_transactions t ON t.id = p.transaction_id
SET p.deleted = TRUE,
    p.awaiting_source_restore = TRUE,
    p.updated_by = t.updated_by,
    p.updated_at = UTC_TIMESTAMP(6)
WHERE p.deleted = FALSE AND t.deleted = TRUE;
