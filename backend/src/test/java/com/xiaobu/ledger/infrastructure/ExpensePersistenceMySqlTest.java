package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.ExpenseApplicationService;
import com.xiaobu.ledger.application.ExpenseResult;
import com.xiaobu.ledger.application.ListExpensesQuery;
import com.xiaobu.ledger.application.RecordExpenseCommand;
import com.xiaobu.ledger.domain.Expense;
import com.xiaobu.ledger.domain.ExpenseDetails;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@EnabledIfEnvironmentVariable(
    named = "XIAOBU_DEV_DB_URL",
    matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/xiaobu_expense_test(?:\\?.*)?")
@SpringBootTest(
    classes = XiaobuApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-expense")
@Transactional
class ExpensePersistenceMySqlTest {

  @Autowired private ExpenseApplicationService expenses;
  @Autowired private ExpensePersistenceAdapter expensePersistence;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void recordsExactCentsAndInitialUtcAuditFields() {
    String ledgerId = newLedgerId();
    var saved = record(ledgerId, "alice", LocalDate.of(2026, 10, 31));
    entityManager.flush();

    var audit =
        jdbc.queryForMap(
            """
            SELECT ledger_id, amount_cents, direction, created_by, updated_by,
                   IF(created_at = updated_at, 1, 0) AS same_audit_time,
                   ABS(TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), created_at)) AS utc_offset,
                   IF(deleted, 1, 0) AS deleted_flag
            FROM ledger_transactions WHERE id = ?
            """,
            saved.id());

    assertEquals(ledgerId, audit.get("ledger_id"));
    assertEquals(1230L, ((Number) audit.get("amount_cents")).longValue());
    assertEquals("EXPENSE", audit.get("direction"));
    assertEquals("alice", audit.get("created_by"));
    assertEquals("alice", audit.get("updated_by"));
    assertEquals(1, ((Number) audit.get("same_audit_time")).intValue());
    assertTrue(((Number) audit.get("utc_offset")).longValue() < 30);
    assertEquals(0, ((Number) audit.get("deleted_flag")).intValue());
  }

  @Test
  void assignedIdConflictDoesNotUpdateAnExistingExpense() {
    String ledgerId = newLedgerId();
    var date = LocalDate.of(2026, 10, 31);
    var saved = record(ledgerId, "alice", date);
    entityManager.flush();
    entityManager.clear();

    var conflict = new Expense(saved.id(), new ExpenseDetails(9900, date, "DINING", "冲突记录"));
    assertThrows(
        ConstraintViolationException.class,
        () -> {
          expensePersistence.add(ledgerId, "alice", conflict);
          entityManager.flush();
        });
  }

  @Test
  void findsOnlyActiveExpensesForTheBookCreatorAndInclusiveMonth() {
    String ledgerId = newLedgerId();
    var october = YearMonth.of(2026, 10);

    var first = record(ledgerId, "alice", october.atDay(1));
    var last = record(ledgerId, "alice", october.atEndOfMonth());
    var deleted = record(ledgerId, "alice", october.atDay(3));
    var income = record(ledgerId, "alice", october.atDay(5));
    record(ledgerId, "alice", october.atDay(1).minusDays(1));
    record(ledgerId, "alice", october.plusMonths(1).atDay(1));
    record(ledgerId, "bob", october.atDay(4));
    record(newLedgerId(), "alice", october.atDay(5));
    entityManager.flush();
    jdbc.update("UPDATE ledger_transactions SET deleted = 1 WHERE id = ?", deleted.id());
    jdbc.update("UPDATE ledger_transactions SET direction = 'INCOME' WHERE id = ?", income.id());
    entityManager.clear();

    var found = expenses.listExpenses(new ListExpensesQuery(ledgerId, "alice", october));
    assertEquals(List.of(last, first), found);
  }

  private ExpenseResult record(String ledgerId, String actorId, LocalDate date) {
    return expenses.recordExpense(
        new RecordExpenseCommand(ledgerId, actorId, 1230, date, "DINING", " 午饭 "));
  }

  private static String newLedgerId() {
    return "book_test_" + UUID.randomUUID();
  }
}
