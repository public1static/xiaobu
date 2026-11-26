package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.ChangeLedgerMemberRoleCommand;
import com.xiaobu.ledger.application.CreateLedgerAccountCommand;
import com.xiaobu.ledger.application.CreateLedgerInvitationCommand;
import com.xiaobu.ledger.application.LedgerAccountApplicationService;
import com.xiaobu.ledger.application.LedgerAccountResult;
import com.xiaobu.ledger.application.LedgerAccountTransferApplicationService;
import com.xiaobu.ledger.application.LedgerInvitationApplicationService;
import com.xiaobu.ledger.application.LedgerMemberApplicationService;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import com.xiaobu.ledger.application.RecordLedgerAccountTransferCommand;
import com.xiaobu.ledger.application.RecordLedgerTransactionCommand;
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
import org.springframework.transaction.annotation.Transactional;

@EnabledIfEnvironmentVariable(
    named = "XIAOBU_DEV_DB_URL",
    matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/xiaobu_expense_test(?:\\?.*)?")
@SpringBootTest(
    classes = XiaobuApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-expense")
@Transactional
class LedgerAccountTransferPersistenceMySqlTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 18);

  @Autowired private LedgerAccountApplicationService accounts;
  @Autowired private LedgerAccountTransferApplicationService transfers;
  @Autowired private LedgerTransactionApplicationService transactions;
  @Autowired private LedgerInvitationApplicationService invites;
  @Autowired private LedgerMemberApplicationService members;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void adjustsBothBalancesWithoutChangingMonthlyIncomeOrExpense() {
    var ledgerId = createLedger("alpha");
    var source = addAccount(ledgerId, "alpha", "现金", 4000);
    var target = addAccount(ledgerId, "alpha", "银行卡", -1000);
    assertEquals(4000, source.balanceCents());
    assertEquals(-1000, target.balanceCents());
    assertEquals(0, addAccount(ledgerId, "alpha", "其他", 0).balanceCents());

    var expense =
        transactions.recordLedgerTransaction(
            new RecordLedgerTransactionCommand(
                ledgerId,
                "alpha",
                TransactionDirection.EXPENSE,
                500,
                DATE,
                "DINING",
                "",
                source.id()));
    var transfer = transfers.recordTransfer(command(ledgerId, "alpha", source.id(), target.id()));

    assertEquals(2300, account(ledgerId, "alpha", source.id()).balanceCents());
    assertEquals(-500, account(ledgerId, "alpha", source.id()).recordedBalanceCents());
    assertEquals(200, account(ledgerId, "alpha", target.id()).balanceCents());
    assertEquals(-1000, account(ledgerId, "alpha", target.id()).openingBalanceCents());
    assertEquals(
        java.util.List.of(expense),
        transactions
            .listLedgerTransactions(
                new ListLedgerTransactionsQuery(ledgerId, "alpha", YearMonth.from(DATE)))
            .entries());
    assertEquals(java.util.List.of(transfer), transfers.listRecentTransfers(ledgerId, "alpha"));
  }

  @Test
  void rejectsTransfersToTheSameOrAnotherLedgersAccount() {
    var ledgerId = createLedger("alpha");
    var other = createLedger("beta");
    var source = addAccount(ledgerId, "alpha", "现金", 4000);
    var target = addAccount(other, "beta", "银行卡", 1000);

    assertThrows(
        FoundationException.class,
        () -> transfers.recordTransfer(command(ledgerId, "alpha", source.id(), source.id())));
    assertThrows(
        FoundationException.class,
        () -> transfers.recordTransfer(command(ledgerId, "alpha", source.id(), target.id())));
    assertEquals(4000, account(ledgerId, "alpha", source.id()).balanceCents());
    assertEquals(1000, account(other, "beta", target.id()).balanceCents());
    assertEquals(java.util.List.of(), transfers.listRecentTransfers(ledgerId, "alpha"));
  }

  @Test
  void sharedReadersCanSeeTransfersButOnlyWritersCanRecordThem() {
    var ledgerId = createLedger("alpha");
    var source = addAccount(ledgerId, "alpha", "现金", 4000);
    var target = addAccount(ledgerId, "alpha", "银行卡", 1000);
    var invitation =
        invites.createLedgerInvitation(new CreateLedgerInvitationCommand(ledgerId, "alpha", "测试甲"));
    invites.acceptLedgerInvitation(invitation.token(), "beta");

    assertEquals(java.util.List.of(), transfers.listRecentTransfers(ledgerId, "beta"));
    assertEquals(
        ErrorCategory.FORBIDDEN,
        assertThrows(
                FoundationException.class,
                () -> transfers.recordTransfer(command(ledgerId, "beta", source.id(), target.id())))
            .category());

    members.changeLedgerMemberRole(
        new ChangeLedgerMemberRoleCommand(ledgerId, "alpha", "beta", LedgerMemberRole.CAN_RECORD));
    var transfer = transfers.recordTransfer(command(ledgerId, "beta", source.id(), target.id()));
    assertEquals(java.util.List.of(transfer), transfers.listRecentTransfers(ledgerId, "alpha"));
    assertEquals(2200, account(ledgerId, "beta", target.id()).balanceCents());
  }

  // 改名或停用保留账户历史；改转账和撤销同时修正两个余额。
  @Test
  void managesAccountHistoryAcrossCorrections() {
    String ledger = createLedger("alpha");
    var cash = addAccount(ledger, "alpha", "现金", 4000);
    var card = addAccount(ledger, "alpha", "信用卡", -500);
    var original = transfers.recordTransfer(command(ledger, "alpha", cash.id(), card.id()));
    var history = accounts.listAccountHistory(ledger, "alpha", card.id());
    assertEquals(original.id(), history.getFirst().getId());
    assertEquals(1, history.getFirst().getOwnRecord());
    assertEquals(DATE.toString(), history.getFirst().getBookedOn());
    accounts.updateLedgerAccount(ledger, "alpha", cash.id(), "我的现金", "现金", false, null, true);
    assertEquals(2800, account(ledger, "alpha", cash.id()).balanceCents());
    transfers.updateTransfer(
        original.id(),
        new RecordLedgerAccountTransferCommand(
            ledger, "alpha", cash.id(), card.id(), 300, DATE, "修改"));
    assertEquals(-200, account(ledger, "alpha", card.id()).balanceCents());
    transfers.cancelTransfer(ledger, "alpha", original.id());
    assertEquals(4000, account(ledger, "alpha", cash.id()).balanceCents());
    assertEquals(0, accounts.listAccountHistory(ledger, "alpha", cash.id()).size());
    assertThrows(
        FoundationException.class,
        () -> transfers.recordTransfer(command(ledger, "alpha", cash.id(), card.id())));
  }

  private RecordLedgerAccountTransferCommand command(
      String ledgerId, String actorId, String source, String target) {
    return new RecordLedgerAccountTransferCommand(
        ledgerId, actorId, source, target, 1200, DATE, "测试");
  }

  private LedgerAccountResult addAccount(
      String ledgerId, String actorId, String name, long balance) {
    return accounts.createLedgerAccount(
        new CreateLedgerAccountCommand(ledgerId, actorId, name, balance));
  }

  private LedgerAccountResult account(String ledgerId, String actorId, String accountId) {
    return accounts.listLedgerAccounts(ledgerId, actorId).stream()
        .filter(account -> account.id().equals(accountId))
        .findFirst()
        .orElseThrow();
  }

  private String createLedger(String ownerId) {
    String id = "transfer_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", id, ownerId);
    return id;
  }
}
