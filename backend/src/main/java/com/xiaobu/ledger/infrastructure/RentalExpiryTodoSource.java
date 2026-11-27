package com.xiaobu.ledger.infrastructure;

import org.springframework.stereotype.Component;

@Component
class RentalExpiryTodoSource implements LedgerTodoSource {

  private static final String ITEMS =
      """
      SELECT 'RENTAL_EXPIRY' AS source, c.id AS source_id, CAST(c.ends_on AS CHAR) AS occurrence,
        c.ends_on AS due_on, %s AS todo_status, h.name AS title,
        c.landlord_name AS subtitle, CAST(NULL AS SIGNED) AS amount_cents
      FROM rental_contracts c
      JOIN rental_homes h ON h.id = c.home_id AND h.ledger_id = c.ledger_id AND h.deleted = 0
      WHERE c.ledger_id = :ledgerId AND c.deleted = 0
      """;

  @Override
  public String openItemsSql() {
    return ITEMS.formatted(
            "IF(DATE_SUB(c.ends_on, INTERVAL c.remind_days DAY) <= :today, 'PENDING', 'PLANNED')")
        + " AND (c.handled_end IS NULL OR c.handled_end <> c.ends_on)"
        + " AND (:pendingOnly = 0 OR DATE_SUB(c.ends_on, INTERVAL c.remind_days DAY) <= :today)";
  }

  @Override
  public String completedItemsSql() {
    return ITEMS.formatted("'COMPLETED'") + " AND c.handled_end = c.ends_on";
  }
}
