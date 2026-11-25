package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.ChangeLedgerMemberRoleCommand;
import com.xiaobu.ledger.application.CreateLedgerInvitationCommand;
import com.xiaobu.ledger.application.ExpenseApplicationService;
import com.xiaobu.ledger.application.LedgerInvitationApplicationService;
import com.xiaobu.ledger.application.LedgerMemberApplicationService;
import com.xiaobu.ledger.application.ListExpensesQuery;
import com.xiaobu.ledger.application.RecordExpenseCommand;
import com.xiaobu.ledger.application.RemoveLedgerMemberCommand;
import com.xiaobu.ledger.domain.LedgerMemberRole;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
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
class LedgerInvitationPersistenceMySqlTest {

  @Autowired private LedgerInvitationApplicationService invites;
  @Autowired private ExpenseApplicationService expenses;
  @Autowired private LedgerMemberApplicationService members;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;

  @Test
  void grantsReadAccessOnlyAfterExplicitAcceptanceAndDoesNotDuplicateMembership() {
    String ledgerId = createLedger();
    var date = LocalDate.of(2026, 10, 31);
    var record =
        expenses.recordExpense(
            new RecordExpenseCommand(ledgerId, "alpha", 1230, date, "DINING", "午饭"));

    var invite =
        invites.createLedgerInvitation(new CreateLedgerInvitationCommand(ledgerId, "alpha", "测试甲"));

    var preview = invites.previewLedgerInvitation(invite.token(), null);
    assertEquals("测试甲", preview.inviterName());
    assertFalse(preview.accepted());
    assertTrue(invites.listJoinedLedgers("beta").isEmpty());
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(
                FoundationException.class,
                () ->
                    expenses.listExpenses(
                        new ListExpensesQuery(ledgerId, "beta", YearMonth.from(date))))
            .category());

    var joined = invites.acceptLedgerInvitation(invite.token(), "beta");
    assertEquals(ledgerId, joined.ledgerId());
    assertEquals(joined, invites.acceptLedgerInvitation(invite.token(), "beta"));
    assertTrue(invites.previewLedgerInvitation(invite.token(), "beta").accepted());
    assertEquals(1, invites.listJoinedLedgers("beta").size());
    assertEquals(
        record,
        expenses
            .listExpenses(new ListExpensesQuery(ledgerId, "beta", YearMonth.from(date)))
            .getFirst());

    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(
                FoundationException.class,
                () ->
                    expenses.recordExpense(
                        new RecordExpenseCommand(ledgerId, "beta", 200, date, "DINING", "")))
            .category());
    assertEquals(
        ErrorCategory.NOT_FOUND,
        assertThrows(
                FoundationException.class,
                () -> invites.previewLedgerInvitation(invite.token(), "gamma"))
            .category());
    assertEquals(
        ErrorCategory.NOT_FOUND,
        assertThrows(
                FoundationException.class,
                () -> invites.acceptLedgerInvitation(invite.token(), "gamma"))
            .category());
  }

  @Test
  void rejectsExpiryAndSelfAcceptanceAndOnlyPersistsTokenDigest() {
    String ledgerId = createLedger();
    var invite =
        invites.createLedgerInvitation(new CreateLedgerInvitationCommand(ledgerId, "alpha", "测试甲"));
    entities.flush();
    var digest =
        jdbc.queryForObject(
            "SELECT token_digest FROM ledger_invitations WHERE ledger_id = ?",
            String.class,
            ledgerId);
    assertTrue(digest.matches("[0-9a-f]{64}"));
    assertFalse(digest.equals(invite.token()));

    assertEquals(
        ErrorCategory.CONFLICT,
        assertThrows(
                FoundationException.class,
                () -> invites.acceptLedgerInvitation(invite.token(), "alpha"))
            .category());
    jdbc.update(
        "UPDATE ledger_invitations SET expires_at = UTC_TIMESTAMP() - INTERVAL 1 MINUTE WHERE ledger_id = ?",
        ledgerId);
    entities.clear();
    assertEquals(
        ErrorCategory.NOT_FOUND,
        assertThrows(
                FoundationException.class,
                () -> invites.previewLedgerInvitation(invite.token(), null))
            .category());
    assertEquals(
        ErrorCategory.NOT_FOUND,
        assertThrows(
                FoundationException.class,
                () -> invites.acceptLedgerInvitation(invite.token(), "beta"))
            .category());
  }

  @Test
  void roleChangesAffectWritesAndOldLinksCannotRestoreRemovedMember() {
    String ledgerId = createLedger();
    var date = LocalDate.of(2026, 10, 31);
    var owner = new CreateLedgerInvitationCommand(ledgerId, "alpha", "测试甲");
    var accepted = invites.createLedgerInvitation(owner);
    var unused = invites.createLedgerInvitation(owner);
    invites.acceptLedgerInvitation(accepted.token(), "beta");

    var draft = new RecordExpenseCommand(ledgerId, "beta", 5400, date, "DINING", "乙记账");
    assertEquals(
        "XIAOBU_LEDGER_READ_ONLY",
        assertThrows(FoundationException.class, () -> expenses.recordExpense(draft))
            .errorCode()
            .value());
    members.changeLedgerMemberRole(
        new ChangeLedgerMemberRoleCommand(ledgerId, "alpha", "beta", LedgerMemberRole.CAN_RECORD));
    var saved = expenses.recordExpense(draft);
    assertEquals(
        LedgerMemberRole.CAN_RECORD,
        invites.acceptLedgerInvitation(accepted.token(), "beta").role());
    entities.flush();
    assertEquals(
        "beta",
        jdbc.queryForObject(
            "SELECT created_by FROM ledger_transactions WHERE id = ?", String.class, saved.id()));

    members.changeLedgerMemberRole(
        new ChangeLedgerMemberRoleCommand(ledgerId, "alpha", "beta", LedgerMemberRole.READ_ONLY));
    assertEquals(
        "XIAOBU_LEDGER_READ_ONLY",
        assertThrows(FoundationException.class, () -> expenses.recordExpense(draft))
            .errorCode()
            .value());
    assertEquals(
        1,
        expenses
            .listExpenses(new ListExpensesQuery(ledgerId, "beta", YearMonth.from(date)))
            .size());
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(FoundationException.class, () -> members.listLedgerMembers(ledgerId, "beta"))
            .category());
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(
                FoundationException.class,
                () ->
                    members.changeLedgerMemberRole(
                        new ChangeLedgerMemberRoleCommand(
                            ledgerId, "beta", "beta", LedgerMemberRole.CAN_RECORD)))
            .category());

    members.removeLedgerMember(new RemoveLedgerMemberCommand(ledgerId, "alpha", "beta"));
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(
                FoundationException.class,
                () ->
                    expenses.listExpenses(
                        new ListExpensesQuery(ledgerId, "beta", YearMonth.from(date))))
            .category());
    assertTrue(invites.listJoinedLedgers("beta").isEmpty());
    assertEquals(
        ErrorCategory.NOT_FOUND,
        assertThrows(
                FoundationException.class,
                () -> invites.acceptLedgerInvitation(accepted.token(), "beta"))
            .category());
    assertEquals(
        ErrorCategory.NOT_FOUND,
        assertThrows(
                FoundationException.class,
                () -> invites.acceptLedgerInvitation(unused.token(), "beta"))
            .category());
    assertEquals(
        1,
        expenses
            .listExpenses(new ListExpensesQuery(ledgerId, "alpha", YearMonth.from(date)))
            .size());
    var fresh = invites.createLedgerInvitation(owner);
    assertEquals(
        LedgerMemberRole.READ_ONLY, invites.acceptLedgerInvitation(fresh.token(), "beta").role());
  }

  private String createLedger() {
    String id = "book_test_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, 'alpha')", id);
    return id;
  }
}
