package com.xiaobu.ledger.infrastructure;

public final class LedgerStatementSql {
  private LedgerStatementSql() {}

  // 流水与到账退款共用账本、删除和日期条件。
  public static final String STATEMENT =
      """
      FROM (
        SELECT t.id, t.direction, t.amount_cents, t.booked_on, t.category_code, t.note,
               t.account_id, NULL AS original_id, t.created_at, 0 AS kind, t.created_by AS recorder
        FROM ledger_transactions t
        WHERE t.ledger_id = :ledger AND t.deleted = 0
          AND t.booked_on BETWEEN :from AND :through
          AND (:keyword = '' OR LOCATE(LOWER(:keyword), LOWER(t.note)) > 0)
        UNION ALL
        SELECT r.id, original.direction, r.amount_cents, r.received_on, original.category_code,
               original.note, r.account_id, original.id, r.created_at, 1, r.created_by
        FROM ledger_refunds r
        INNER JOIN ledger_transactions original
            ON original.id = r.transaction_id AND original.ledger_id = r.ledger_id
        WHERE r.ledger_id = :ledger AND original.deleted = 0
          AND r.received_on BETWEEN :from AND :through
          AND (:keyword = '' OR LOCATE(LOWER(:keyword), LOWER(original.note)) > 0
              OR LOCATE(LOWER(:keyword), '退款') > 0)
      ) AS activity
      WHERE (:category IS NULL OR category_code = :category)
        AND (:account IS NULL OR account_id = :account)
        AND (:unassigned = 0 OR account_id IS NULL)
        AND (:direction IS NULL OR direction = :direction)
        AND (:recorder IS NULL OR recorder = :recorder)
      """;
}
