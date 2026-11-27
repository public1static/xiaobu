package com.xiaobu.ledger.infrastructure;

import org.springframework.stereotype.Component;

@Component
class ManualReminderTodoSource implements LedgerTodoSource {

  @Override
  public String openItemsSql() {
    return """
    SELECT 'MANUAL_REMINDER' AS source, r.id AS source_id, CAST(r.revision AS CHAR) AS occurrence,
      r.due_on AS due_on, IF(r.remind_at <= CAST(:now AS DATETIME), 'PENDING', 'PLANNED') AS todo_status,
      r.title, DATE_FORMAT(r.remind_at, '%m-%d %H:%i') AS subtitle,
      CAST(NULL AS SIGNED) AS amount_cents
    FROM ledger_manual_reminders r
    WHERE r.ledger_id = :ledgerId AND r.created_by = :actorId AND r.deleted = 0
      AND r.completed = 0 AND r.stopped = 0
      AND (:pendingOnly = 0 OR r.remind_at <= CAST(:now AS DATETIME))
    """;
  }

  @Override
  public String completedItemsSql() {
    return """
    SELECT 'MANUAL_REMINDER' AS source, r.id AS source_id, CAST(o.revision AS CHAR) AS occurrence,
      o.due_on AS due_on, 'COMPLETED' AS todo_status, o.title,
      DATE_FORMAT(o.remind_at, '%m-%d %H:%i') AS subtitle, CAST(NULL AS SIGNED) AS amount_cents
    FROM ledger_manual_reminders r
    JOIN ledger_manual_reminder_occurrences o ON o.reminder_id = r.id AND o.deleted = 0
    WHERE r.ledger_id = :ledgerId AND r.created_by = :actorId AND r.deleted = 0
    """;
  }
}
