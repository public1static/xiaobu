package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.LedgerTransactionResult;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import com.xiaobu.ledger.application.RecordLedgerTransactionCommand;
import com.xiaobu.ledger.domain.LedgerTransaction;
import com.xiaobu.ledger.domain.LedgerTransactionDetails;
import com.xiaobu.ledger.domain.TransactionDirection;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class LedgerTransactionPersistenceMySqlTest {

  @Autowired private LedgerTransactionApplicationService expenses;
  @Autowired private LedgerTransactionPersistenceAdapter expensePersistence;
  @Autowired private PersonalLedgerApplicationService ledgers;
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
  void assignedIdConflictDoesNotUpdateAnExistingLedgerTransaction() {
    String ledgerId = newLedgerId();
    var date = LocalDate.of(2026, 10, 31);
    var saved = record(ledgerId, "alice", date);
    entityManager.flush();
    entityManager.clear();

    var conflict =
        new LedgerTransaction(
            saved.id(),
            new LedgerTransactionDetails(
                TransactionDirection.EXPENSE, 9900, date, "DINING", "冲突记录"));
    assertThrows(
        ConstraintViolationException.class,
        () -> {
          expensePersistence.add(ledgerId, "alice", conflict);
          entityManager.flush();
        });
  }

  @Test
  void findsOnlyActiveLedgerTransactionsForTheOwnedLedgerAndInclusiveMonth() {
    String ledgerId = newLedgerId();
    var october = YearMonth.of(2026, 10);

    var first = record(ledgerId, "alice", october.atDay(1));
    var last = record(ledgerId, "alice", october.atEndOfMonth());
    var deleted = record(ledgerId, "alice", october.atDay(3));
    var income =
        expenses.recordLedgerTransaction(
            new RecordLedgerTransactionCommand(
                ledgerId,
                "alice",
                TransactionDirection.INCOME,
                500000,
                october.atDay(5),
                "SALARY",
                "工资"));
    record(ledgerId, "alice", october.atDay(1).minusDays(1));
    record(ledgerId, "alice", october.plusMonths(1).atDay(1));
    record(newLedgerId("bob"), "bob", october.atDay(5));
    entityManager.flush();
    jdbc.update("UPDATE ledger_transactions SET deleted = 1 WHERE id = ?", deleted.id());
    entityManager.clear();

    var found =
        expenses.listLedgerTransactions(
            new ListLedgerTransactionsQuery(ledgerId, "alice", october));
    assertEquals(List.of(last, income, first), found);
    assertEquals(
        "INCOME",
        jdbc.queryForObject(
            "SELECT direction FROM ledger_transactions WHERE id = ?", String.class, income.id()));
    assertEquals(
        500000L,
        jdbc.queryForObject(
            "SELECT amount_cents FROM ledger_transactions WHERE id = ?", Long.class, income.id()));
  }

  private LedgerTransactionResult record(String ledgerId, String actorId, LocalDate date) {
    return expenses.recordLedgerTransaction(
        new RecordLedgerTransactionCommand(
            ledgerId, actorId, TransactionDirection.EXPENSE, 1230, date, "DINING", " 午饭 "));
  }

  @Test
  void deniesBothReadsAndWritesToABookOwnedByAnotherActor() {
    String ledgerId = newLedgerId();
    var date = LocalDate.of(2026, 10, 31);
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(FoundationException.class, () -> record(ledgerId, "bob", date)).category());
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(
                FoundationException.class,
                () ->
                    expenses.listLedgerTransactions(
                        new ListLedgerTransactionsQuery(ledgerId, "bob", YearMonth.from(date))))
            .category());
    entityManager.flush();
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_transactions WHERE ledger_id = ?",
            Integer.class,
            ledgerId));
  }

  @Test
  void simultaneousOpeningKeepsOnePersonalLedger() throws Exception {
    String owner = "actor_" + UUID.randomUUID();
    var gate = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(() -> {
            gate.await(8, TimeUnit.SECONDS);
            return ledgers.openPersonalLedger(owner);
          });
      var second =
          pool.submit(() -> {
            gate.await(8, TimeUnit.SECONDS);
            return ledgers.openPersonalLedger(owner);
          });
      assertEquals(first.get(12, TimeUnit.SECONDS), second.get(12, TimeUnit.SECONDS));
    }
  }

  private String newLedgerId() {
    return newLedgerId("alice");
  }

  private String newLedgerId(String ownerId) {
    String id = "book_test_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", id, ownerId);
    return id;
  }
}
