ALTER TABLE fixed_bill_payments
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN active_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted = FALSE THEN 1 ELSE NULL END
    ) STORED,
    DROP INDEX uk_fixed_bill_period,
    DROP INDEX uk_fixed_bill_transaction,
    ADD UNIQUE KEY uk_fixed_bill_period_active (bill_id, period_start, active_slot),
    ADD UNIQUE KEY uk_fixed_bill_transaction_active (transaction_id, active_slot);
