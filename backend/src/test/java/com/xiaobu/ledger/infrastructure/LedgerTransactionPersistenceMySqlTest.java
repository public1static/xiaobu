package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.ChangeLedgerCategoryOrderCommand;
import com.xiaobu.ledger.application.ChangeLedgerCategoryStatusCommand;
import com.xiaobu.ledger.application.ChangeLedgerTransactionStateCommand;
import com.xiaobu.ledger.application.CreateLedgerAccountCommand;
import com.xiaobu.ledger.application.CreateLedgerCategoryCommand;
import com.xiaobu.ledger.application.EditLedgerTransactionCommand;
import com.xiaobu.ledger.application.LedgerAccountApplicationService;
import com.xiaobu.ledger.application.LedgerCategoryApplicationService;
import com.xiaobu.ledger.application.LedgerCategoryResult;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.LedgerTransactionResult;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import com.xiaobu.ledger.application.RecordLedgerRefundCommand;
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
  @Autowired private LedgerCategoryApplicationService categories;
  @Autowired private LedgerAccountApplicationService accounts;
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
            "alice",
            new LedgerTransactionDetails(
                TransactionDirection.EXPENSE, 9900, date, "DINING", "冲突记录", null),
            0,
            false);
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
                "工资",
                null));
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
            ledgerId, actorId, TransactionDirection.EXPENSE, 1230, date, "DINING", " 午饭 ", null));
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

  @Test
  void accountsRespectLedgerOwnershipAndNetAmounts() {
    String alpha = newLedgerId();
    String beta = newLedgerId("bob");
    var cash = accounts.createLedgerAccount(new CreateLedgerAccountCommand(alpha, "alice", "现金"));
    var other = accounts.createLedgerAccount(new CreateLedgerAccountCommand(beta, "bob", "现金"));
    var date = LocalDate.of(2026, 10, 31);
    assertEquals(
        ErrorCategory.CONFLICT,
        assertThrows(
                FoundationException.class,
                () ->
                    accounts.createLedgerAccount(
                        new CreateLedgerAccountCommand(alpha, "alice", " 现金 ")))
            .category());
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(FoundationException.class, () -> accounts.listLedgerAccounts(alpha, "bob"))
            .category());
    assertEquals(
        "XIAOBU_ACCOUNT_UNAVAILABLE",
        assertThrows(
                FoundationException.class,
                () ->
                    expenses.recordLedgerTransaction(
                        new RecordLedgerTransactionCommand(
                            alpha,
                            "alice",
                            TransactionDirection.EXPENSE,
                            1230,
                            date,
                            "DINING",
                            "",
                            other.id())))
            .errorCode()
            .value());
    expenses.recordLedgerTransaction(
        new RecordLedgerTransactionCommand(
            alpha, "alice", TransactionDirection.INCOME, 10000, date, "SALARY", "工资", cash.id()));
    var deleted =
        expenses.recordLedgerTransaction(
            new RecordLedgerTransactionCommand(
                alpha,
                "alice",
                TransactionDirection.EXPENSE,
                1230,
                date,
                "DINING",
                "午饭",
                cash.id()));
    record(alpha, "alice", date);
    assertEquals(
        8770, accounts.listLedgerAccounts(alpha, "alice").getFirst().recordedBalanceCents());
    entityManager.flush();
    jdbc.update("UPDATE ledger_transactions SET deleted = 1 WHERE id = ?", deleted.id());
    entityManager.clear();
    assertEquals(
        10000, accounts.listLedgerAccounts(alpha, "alice").getFirst().recordedBalanceCents());
    assertEquals(0, accounts.listLedgerAccounts(beta, "bob").getFirst().recordedBalanceCents());
  }

  @Test
  void searchesInclusiveCrossMonthRangeAndRestrictsDetailToLedger() {
    String alpha = newLedgerId();
    String beta = newLedgerId("bob");
    var from = LocalDate.of(2026, 10, 31);
    var through = from.plusDays(1);
    var account =
        accounts.createLedgerAccount(new CreateLedgerAccountCommand(alpha, "alice", "现金"));
    var matched =
        expenses.recordLedgerTransaction(
            new RecordLedgerTransactionCommand(
                alpha,
                "alice",
                TransactionDirection.EXPENSE,
                1230,
                from,
                "DINING",
                "午饭 A%",
                account.id()));
    var otherDay =
        expenses.recordLedgerTransaction(
            new RecordLedgerTransactionCommand(
                alpha,
                "alice",
                TransactionDirection.INCOME,
                3000,
                through,
                "BONUS",
                "活动 a%",
                null));
    record(alpha, "alice", through.plusDays(1));

    var query = new ListLedgerTransactionsQuery(alpha, "alice", from, through, " a% ");
    assertEquals(List.of(otherDay, matched), expenses.listLedgerTransactions(query));

    var detail = expenses.getLedgerTransaction(alpha, "alice", matched.id());
    assertEquals(matched, detail.transaction());
    assertEquals("alice", detail.createdBy());
    assertEquals("现金", detail.accountName());
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(
                FoundationException.class,
                () -> expenses.getLedgerTransaction(alpha, "bob", matched.id()))
            .category());
    assertEquals(
        ErrorCategory.NOT_FOUND,
        assertThrows(
                FoundationException.class,
                () -> expenses.getLedgerTransaction(beta, "bob", matched.id()))
            .category());
    assertThrows(
        FoundationException.class,
        () -> new ListLedgerTransactionsQuery(alpha, "alice", through, from, ""));
  }

  @Test
  void managesCategoriesPerLedgerAndKeepsInactiveHistoryEditable() {
    String alpha = newLedgerId();
    String beta = newLedgerId("bob");
    var direction = TransactionDirection.EXPENSE;
    var created =
        categories.createLedgerCategory(
            new CreateLedgerCategoryCommand(alpha, "alice", direction, "咖啡"));
    var order =
        new java.util.ArrayList<>(
            categories.listLedgerCategories(alpha, "alice").stream()
                .filter(item -> item.direction() == direction)
                .map(LedgerCategoryResult::code)
                .toList());
    order.remove(created.code());
    order.addFirst(created.code());
    var reordered =
        categories.changeLedgerCategoryOrder(
            new ChangeLedgerCategoryOrderCommand(alpha, "alice", direction, order));
    assertEquals(created.code(), reordered.getFirst().code());
    var date = LocalDate.of(2026, 10, 31);
    var command =
        new RecordLedgerTransactionCommand(
            alpha, "alice", direction, 1230, date, created.code(), "咖啡记录", null);
    var saved = expenses.recordLedgerTransaction(command);
    assertThrows(
        FoundationException.class,
        () ->
            expenses.recordLedgerTransaction(
                new RecordLedgerTransactionCommand(
                    beta, "bob", direction, 1230, date, created.code(), "", null)));
    assertThrows(
        FoundationException.class,
        () ->
            categories.changeLedgerCategoryStatus(
                new ChangeLedgerCategoryStatusCommand(alpha, "bob", created.code(), false)));
    var updated =
        categories.changeLedgerCategoryStatus(
            new ChangeLedgerCategoryStatusCommand(alpha, "alice", created.code(), false));
    assertFalse(
        updated.stream()
            .filter(item -> item.code().equals(created.code()))
            .findFirst()
            .orElseThrow()
            .active());
    assertThrows(FoundationException.class, () -> expenses.recordLedgerTransaction(command));
    expenses.editLedgerTransaction(
        new EditLedgerTransactionCommand(
            alpha, "alice", saved.id(), 0, direction, 1230, date, created.code(), "补充备注", null));
    assertEquals(
        created.code(),
        expenses.getLedgerTransaction(alpha, "alice", saved.id()).transaction().categoryCode());
    categories.changeLedgerCategoryStatus(
        new ChangeLedgerCategoryStatusCommand(alpha, "alice", created.code(), true));
    assertEquals(created.code(), expenses.recordLedgerTransaction(command).categoryCode());
  }

  @Test
  void refundsAreProjectedOnReceiptDateAndFollowLogicalDeletionOfOriginal() {
    String ledgerId = newLedgerId();
    var paymentDate = LocalDate.of(2026, 8, 31);
    var receiptDate = paymentDate.plusDays(1);
    var payer =
        accounts.createLedgerAccount(new CreateLedgerAccountCommand(ledgerId, "alice", "原付款卡"));
    var receiver =
        accounts.createLedgerAccount(new CreateLedgerAccountCommand(ledgerId, "alice", "收退款卡"));
    var original =
        expenses.recordLedgerTransaction(
            new RecordLedgerTransactionCommand(
                ledgerId,
                "alice",
                TransactionDirection.EXPENSE,
                4000,
                paymentDate,
                "DINING",
                "午饭",
                payer.id()));
    var partial =
        expenses.recordLedgerRefund(
            new RecordLedgerRefundCommand(
                ledgerId, original.id(), "alice", 0, 1000, receiptDate, receiver.id()));
    assertThrows(
        FoundationException.class,
        () ->
            expenses.recordLedgerRefund(
                new RecordLedgerRefundCommand(
                    ledgerId, original.id(), "alice", 0, 3001, receiptDate, null)));
    assertThrows(
        FoundationException.class,
        () ->
            expenses.editLedgerTransaction(
                new EditLedgerTransactionCommand(
                    ledgerId,
                    "alice",
                    original.id(),
                    0,
                    TransactionDirection.EXPENSE,
                    999,
                    paymentDate,
                    "DINING",
                    "午饭",
                    payer.id())));
    assertThrows(
        FoundationException.class,
        () ->
            expenses.editLedgerTransaction(
                new EditLedgerTransactionCommand(
                    ledgerId,
                    "alice",
                    original.id(),
                    0,
                    TransactionDirection.EXPENSE,
                    4000,
                    receiptDate.plusDays(1),
                    "DINING",
                    "午饭",
                    payer.id())));
    expenses.recordLedgerRefund(
        new RecordLedgerRefundCommand(
            ledgerId, original.id(), "alice", 0, 3000, receiptDate, null));

    var originalMonth =
        new ListLedgerTransactionsQuery(ledgerId, "alice", YearMonth.from(paymentDate));
    var refundMonth =
        new ListLedgerTransactionsQuery(ledgerId, "alice", YearMonth.from(receiptDate));
    assertEquals(List.of(original), expenses.listLedgerTransactions(originalMonth));
    var refundRows = expenses.listLedgerTransactions(refundMonth);
    assertEquals(2, refundRows.size());
    for (var row : refundRows) {
      assertEquals(original.id(), row.originalTransactionId());
      assertEquals(TransactionDirection.EXPENSE, row.direction());
      assertEquals(receiptDate, row.bookedOn());
    }
    var detail = expenses.getLedgerTransaction(ledgerId, "alice", original.id());
    assertEquals(
        List.of(1000L, 3000L),
        detail.refunds().stream().map(refund -> refund.amountCents()).sorted().toList());
    assertEquals(4000, detail.transaction().amountCents());
    assertEquals(
        "alice",
        jdbc.queryForObject(
            "SELECT updated_by FROM ledger_refunds WHERE id = ?", String.class, partial.id()));
    assertEquals(-4000, recordedBalance(ledgerId, payer.id()));
    assertEquals(1000, recordedBalance(ledgerId, receiver.id()));

    expenses.deleteLedgerTransaction(
        new ChangeLedgerTransactionStateCommand(ledgerId, "alice", original.id(), 0));
    assertTrue(expenses.listLedgerTransactions(refundMonth).isEmpty());
    assertEquals(0, recordedBalance(ledgerId, payer.id()));
    assertEquals(0, recordedBalance(ledgerId, receiver.id()));
    entityManager.clear();
    expenses.restoreLedgerTransaction(
        new ChangeLedgerTransactionStateCommand(ledgerId, "alice", original.id(), 1));
    assertEquals(refundRows, expenses.listLedgerTransactions(refundMonth));
    assertEquals(1000, recordedBalance(ledgerId, receiver.id()));
  }

  private long recordedBalance(String ledgerId, String accountId) {
    return accounts.listLedgerAccounts(ledgerId, "alice").stream()
        .filter(account -> account.id().equals(accountId))
        .findFirst()
        .orElseThrow()
        .recordedBalanceCents();
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
