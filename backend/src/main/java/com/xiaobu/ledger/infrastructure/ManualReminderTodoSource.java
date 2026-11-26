package com.xiaobu.ledger.infrastructure;

import org.springframework.stereotype.Component;

@Component
class ManualReminderTodoSource implements LedgerTodoSource {

  private static final String ITEMS =
      """
      SELECT 'MANUAL_REMINDER' AS source, r.id AS source_id, CAST(r.due_on AS CHAR) AS occurrence,
        r.due_on AS due_on, %s AS todo_status, r.title,
        DATE_FORMAT(r.remind_at, '%%m-%%d %%H:%%i') AS subtitle, CAST(NULL AS SIGNED) AS amount_cents
      FROM ledger_manual_reminders r
      WHERE r.ledger_id = :ledgerId AND r.created_by = :actorId AND r.deleted = 0
      """;

  @Override
  public String openItemsSql() {
    return ITEMS.formatted("IF(r.remind_at <= CAST(:now AS DATETIME), 'PENDING', 'PLANNED')")
        + " AND r.completed = 0 AND (:pendingOnly = 0 OR r.remind_at <= CAST(:now AS DATETIME))";
  }

  @Override
  public String completedItemsSql() {
    return ITEMS.formatted("'COMPLETED'") + " AND r.completed = 1";
  }
}
