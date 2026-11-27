package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.ChangeLedgerMemberRoleCommand;
import com.xiaobu.ledger.application.ChangeLedgerTransactionStateCommand;
import com.xiaobu.ledger.application.CreateLedgerInvitationCommand;
import com.xiaobu.ledger.application.LedgerInvitationApplicationService;
import com.xiaobu.ledger.application.LedgerMemberApplicationService;
import com.xiaobu.ledger.application.LedgerMonthlyBudgetApplicationService;
import com.xiaobu.ledger.application.LedgerMonthlyBudgetResult;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.LedgerTransactionResult;
import com.xiaobu.ledger.application.RecordLedgerRefundCommand;
import com.xiaobu.ledger.application.RecordLedgerTransactionCommand;
import com.xiaobu.ledger.application.SetLedgerMonthlyBudgetCommand;
import com.xiaobu.ledger.domain.LedgerMemberRole;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@EnabledIfEnvironmentVariable(
    named = "XIAOBU_DEV_DB_URL",
    matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/xiaobu_expense_test(?:\\?.*)?")
@SpringBootTest(
    classes = XiaobuApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-expense")
class LedgerMonthlyBudgetPersistenceMySqlTest {
  private static final YearMonth MONTH = YearMonth.of(2025, 3);
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LedgerMonthlyBudgetApplicationService budgets;
  @Autowired private LedgerTransactionApplicationService transactions;
  @Autowired private LedgerInvitationApplicationService invitations;
  @Autowired private LedgerMemberApplicationService members;

  @Test
  void usesCompleteMonthlyNetExpenseAndUpdatesOnRefundDeletionAndRestore() {
    var f = fixture();
    assertEquals(new LedgerMonthlyBudgetResult("2025-03", null, 0, null), read(f, MONTH));
    write(f, MONTH, 20000);
    write(f, MONTH.minusMonths(1), 30000);
    for (int i = 0; i < 21; i++) record(f, TransactionDirection.EXPENSE, 1000, MONTH.atDay(1));
    record(f, TransactionDirection.INCOME, 90000, MONTH.atEndOfMonth());
    var refunded =
        record(f, TransactionDirection.EXPENSE, 4500, MONTH.minusMonths(1).atEndOfMonth());
    transactions.recordLedgerRefund(
        new RecordLedgerRefundCommand(
            f.ledger(), refunded.id(), f.actor(), 0, 500, MONTH.atEndOfMonth(), null));
    assertEquals(new LedgerMonthlyBudgetResult("2025-03", 20000L, 20500, -500L), read(f, MONTH));
    assertEquals(4500, read(f, MONTH.minusMonths(1)).usedCents());
    assertEquals(0, read(f, MONTH.plusMonths(1)).usedCents());
    transactions.deleteLedgerTransaction(
        new ChangeLedgerTransactionStateCommand(f.ledger(), f.actor(), refunded.id(), 0));
    assertEquals(21000, read(f, MONTH).usedCents());
    transactions.restoreLedgerTransaction(
        new ChangeLedgerTransactionStateCommand(f.ledger(), f.actor(), refunded.id(), 1));
    assertEquals(20500, read(f, MONTH).usedCents());
    assertEquals(4500, write(f, MONTH, 25000).remainingCents());
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_monthly_budgets WHERE ledger_id = ?",
            Integer.class,
            f.ledger()));
  }

  @Test
  void budgetIsScopedAndSharesLedgerPermissionsAndAudit() {
    var owner = fixture();
    var other = fixture();
    write(owner, MONTH, 50000);
    assertNull(read(other, MONTH).amountCents());
    assertThrows(
        FoundationException.class,
        () -> budgets.getLedgerMonthlyBudget(owner.ledger(), other.actor(), MONTH));
    assertThrows(FoundationException.class, () -> write(owner, MONTH, 0));
    assertThrows(FoundationException.class, () -> write(owner, MONTH, 10_000_000_000L));
    var invite =
        invitations.createLedgerInvitation(
            new CreateLedgerInvitationCommand(owner.ledger(), owner.actor(), "预算测试邀请"));
    invitations.acceptLedgerInvitation(invite.token(), other.actor());
    var shared = new Fixture(owner.ledger(), other.actor());
    assertEquals(50000, read(shared, MONTH).amountCents());
    assertThrows(FoundationException.class, () -> write(shared, MONTH, 48000));
    members.changeLedgerMemberRole(
        new ChangeLedgerMemberRoleCommand(
            owner.ledger(), owner.actor(), other.actor(), LedgerMemberRole.CAN_RECORD));
    write(shared, MONTH, 48000);
    assertEquals(48000, read(owner, MONTH).amountCents());
    var audit =
        jdbc.queryForMap(
            """
            SELECT created_by, updated_by, month_start
            FROM ledger_monthly_budgets WHERE ledger_id = ?
            """,
            owner.ledger());
    assertEquals(owner.actor(), audit.get("created_by"));
    assertEquals(other.actor(), audit.get("updated_by"));
    assertEquals(MONTH.atDay(1), ((java.sql.Date) audit.get("month_start")).toLocalDate());
    assertNull(read(owner, MONTH.plusMonths(1)).amountCents());
  }

  private Fixture fixture() {
    String actor = "budget-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    return new Fixture(ledger, actor);
  }

  private LedgerMonthlyBudgetResult read(Fixture f, YearMonth month) {
    return budgets.getLedgerMonthlyBudget(f.ledger(), f.actor(), month);
  }

  private LedgerMonthlyBudgetResult write(Fixture f, YearMonth month, long cents) {
    return budgets.setLedgerMonthlyBudget(
        new SetLedgerMonthlyBudgetCommand(f.ledger(), f.actor(), month, cents));
  }

  private LedgerTransactionResult record(
      Fixture f, TransactionDirection direction, long cents, LocalDate date) {
    return transactions.recordLedgerTransaction(
        new RecordLedgerTransactionCommand(
            f.ledger(),
            f.actor(),
            direction,
            cents,
            date,
            direction == TransactionDirection.INCOME ? "SALARY" : "DINING",
            "",
            null));
  }

  private record Fixture(String ledger, String actor) {}
}
