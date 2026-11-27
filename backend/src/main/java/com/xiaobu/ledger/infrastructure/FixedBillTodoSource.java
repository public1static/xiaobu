package com.xiaobu.ledger.infrastructure;

import org.springframework.stereotype.Component;

@Component
class FixedBillTodoSource implements LedgerTodoSource {

  // 保持与 FixedBill.dueOn 一致：短月取月末，不移动后续月份的原定付款日。
  private static String dueOn(String month) {
    return "DATE_ADD("
        + month
        + ", INTERVAL (LEAST(DAY(b.first_due_on), DAY(LAST_DAY("
        + month
        + "))) - 1) DAY)";
  }

  private static String validPeriod(String month, String due) {
    return """
    %1$s >= DATE_SUB(b.first_due_on, INTERVAL (DAY(b.first_due_on) - 1) DAY)
    AND MOD(PERIOD_DIFF(EXTRACT(YEAR_MONTH FROM %1$s), EXTRACT(YEAR_MONTH FROM b.first_due_on)), b.cycle_months) = 0
    AND (b.stop_month IS NULL OR %1$s < b.stop_month)
    AND (b.rental_starts_on IS NULL OR %2$s >= b.rental_starts_on)
    AND (b.last_due_on IS NULL OR %2$s <= b.last_due_on)
    """
        .formatted(month, due);
  }

  @Override
  public String openItemsSql() {
    String period = "m.month_start";
    String due = dueOn(period);
    String overdue =
        """
        SELECT 'FIXED_BILL' AS source, b.id AS source_id, LEFT(%1$s, 7) AS occurrence,
          %2$s AS due_on, 'PENDING' AS todo_status, b.name AS title, '' AS subtitle, b.amount_cents
        FROM fixed_bills b
        JOIN ledger_calendar_months m ON %1$s <= :today AND %3$s
        LEFT JOIN fixed_bill_payments p ON p.bill_id = b.id AND p.period_start = %1$s AND p.active_slot = 1
        WHERE b.ledger_id = :ledgerId AND b.deleted = 0 AND p.id IS NULL AND %2$s <= :today
        """
            .formatted(period, due, validPeriod(period, due));

    // 只索引定位首个未付的未来月份，不对每个未来月份反查前一期。
    String next = "next_month.month_start";
    String nextDue = dueOn(next);
    String upcoming =
        """
        SELECT 'FIXED_BILL' AS source, b.id AS source_id, LEFT(%1$s, 7) AS occurrence,
          %2$s AS due_on, IF(%2$s <= DATE_ADD(:today, INTERVAL 3 DAY), 'PENDING', 'PLANNED') AS todo_status,
          b.name AS title, '' AS subtitle, b.amount_cents
        FROM fixed_bills b
        JOIN ledger_calendar_months m ON %1$s = (
          SELECT %3$s FROM ledger_calendar_months next_month
          WHERE %3$s >= DATE_SUB(:today, INTERVAL (DAY(:today) - 1) DAY)
            AND %4$s > :today AND %5$s
            AND (:pendingOnly = 0 OR %3$s <= DATE_ADD(:today, INTERVAL 3 DAY))
            AND NOT EXISTS (
              SELECT 1 FROM fixed_bill_payments p
              WHERE p.bill_id = b.id AND p.period_start = %3$s AND p.active_slot = 1
            )
          ORDER BY %3$s
          LIMIT 1
        )
        WHERE b.ledger_id = :ledgerId AND b.deleted = 0
          AND (:pendingOnly = 0 OR %2$s <= DATE_ADD(:today, INTERVAL 3 DAY))
        """
            .formatted(period, due, next, nextDue, validPeriod(next, nextDue));
    return overdue + " UNION ALL " + upcoming;
  }

  @Override
  public String completedItemsSql() {
    // 收养存量房租计划不隐藏原付款，即使原付款属于后来合同窗口外的月份。
    return """
    SELECT 'FIXED_BILL' AS source, b.id AS source_id, LEFT(p.period_start, 7) AS occurrence,
      %s AS due_on, 'COMPLETED' AS todo_status, b.name AS title, '' AS subtitle, p.amount_cents
    FROM fixed_bills b
    JOIN fixed_bill_payments p ON p.bill_id = b.id AND p.active_slot = 1
    WHERE b.ledger_id = :ledgerId AND b.deleted = 0
    """
        .formatted(dueOn("p.period_start"));
  }
}
