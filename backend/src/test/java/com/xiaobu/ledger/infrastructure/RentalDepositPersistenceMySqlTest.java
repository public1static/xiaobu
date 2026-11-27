package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.CreateLedgerAccountCommand;
import com.xiaobu.ledger.application.CreateRentalContractCommand;
import com.xiaobu.ledger.application.EditRentalDepositCommand;
import com.xiaobu.ledger.application.LedgerAccountApplicationService;
import com.xiaobu.ledger.application.RecordRentalDepositCommand;
import com.xiaobu.ledger.application.RentalApplicationService;
import com.xiaobu.ledger.application.RentalDepositApplicationService;
import com.xiaobu.ledger.application.RentalDepositPageResult;
import com.xiaobu.ledger.application.SaveRentalHomeCommand;
import com.xiaobu.ledger.domain.RentalDepositKind;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;
import java.util.List;
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
class RentalDepositPersistenceMySqlTest {
  private static final LocalDate START = LocalDate.of(2025, 1, 15);
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LedgerAccountApplicationService accounts;
  @Autowired private RentalApplicationService rentals;
  @Autowired private RentalDepositApplicationService deposits;

  @Test
  void accountMovesByActualDepositWithoutCreatingIncomeOrExpense() {
    var f = fixture();
    var card =
        accounts.createLedgerAccount(
            new CreateLedgerAccountCommand(f.ledger(), f.actor(), "银行卡", 300000));
    var cash =
        accounts.createLedgerAccount(
            new CreateLedgerAccountCommand(f.ledger(), f.actor(), "现金", 10000));
    assertEquals(0, list(f).heldCents());
    assertEquals(300000, balance(f, card.id()));
    record(f, RentalDepositKind.PAY, 100000, START.minusDays(1), card.id());
    record(f, RentalDepositKind.REFUND, 30000, START.plusMonths(13), card.id());
    var receipt = list(f).entries().getFirst();
    assertEquals(70000, list(f).heldCents());
    assertEquals(230000, balance(f, card.id()));
    assertEquals(30000, receipt.amountCents());
    assertEquals("银行卡", receipt.accountName());
    var history = accounts.listAccountHistory(f.ledger(), f.actor(), card.id());
    assertEquals(
        List.of("DEPOSIT_REFUND", "DEPOSIT_PAY"),
        history.stream().map(row -> row.getKind()).toList());
    assertEquals("朝阳路公寓", history.getFirst().getRelatedName());
    deposits.editRentalDeposit(
        new EditRentalDepositCommand(
            f.ledger(),
            f.actor(),
            f.contract(),
            receipt.id(),
            receipt.version(),
            40000,
            receipt.bookedOn(),
            cash.id(),
            "改正收款账户"));
    assertEquals(60000, list(f).heldCents());
    assertEquals(200000, balance(f, card.id()));
    assertEquals(50000, balance(f, cash.id()));
    assertEquals(1, list(f).entries().getFirst().version());
    assertEquals(receipt.id(), list(f).entries().getFirst().id());
    assertThrows(
        FoundationException.class,
        () ->
            deposits.editRentalDeposit(
                new EditRentalDepositCommand(
                    f.ledger(),
                    f.actor(),
                    f.contract(),
                    receipt.id(),
                    receipt.version(),
                    30000,
                    receipt.bookedOn(),
                    card.id(),
                    "")));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_transactions WHERE ledger_id = ?",
            Integer.class,
            f.ledger()));
  }

  @Test
  void validatesEachPastDayAndRollsBackInvalidEditsAndCrossLedgerAccounts() {
    var f = fixture();
    var other = fixture();
    var otherCard =
        accounts.createLedgerAccount(
            new CreateLedgerAccountCommand(other.ledger(), other.actor(), "其他账户", 30000));
    assertThrows(
        FoundationException.class,
        () -> record(f, RentalDepositKind.PAY, 100000, START, otherCard.id()));
    record(f, RentalDepositKind.PAY, 100000, START, null);
    record(f, RentalDepositKind.PAY, 60000, START.plusDays(7), null);
    assertThrows(
        FoundationException.class,
        () -> record(f, RentalDepositKind.REFUND, 100, START.minusDays(1), null));
    assertThrows(
        FoundationException.class,
        () -> record(f, RentalDepositKind.REFUND, 120000, START.plusDays(2), null));
    record(f, RentalDepositKind.REFUND, 100000, START.plusDays(2), null);
    var first = list(f).entries().getLast();
    assertThrows(
        FoundationException.class,
        () ->
            deposits.editRentalDeposit(
                new EditRentalDepositCommand(
                    f.ledger(),
                    f.actor(),
                    f.contract(),
                    first.id(),
                    first.version(),
                    90000,
                    START,
                    null,
                    "")));
    assertEquals(60000, list(f).heldCents());
    assertEquals(3, list(f).totalCount());
    assertThrows(
        FoundationException.class,
        () -> deposits.listRentalDeposits(other.ledger(), other.actor(), f.contract(), 0));
  }

  @Test
  void allRowsAffectHeldEvenWhenTheContractHistoryIsPaged() {
    var f = fixture();
    for (int i = 0; i < 21; i++) {
      record(f, RentalDepositKind.PAY, 100, START.plusDays(i), null);
    }
    record(f, RentalDepositKind.REFUND, 100, START, null);
    var first = list(f);
    var second = deposits.listRentalDeposits(f.ledger(), f.actor(), f.contract(), 1);
    assertEquals(22, first.totalCount());
    assertEquals(20, first.entries().size());
    assertEquals(2, second.entries().size());
    assertEquals(2000, second.heldCents());
    assertEquals(2000, first.heldCents());
  }

  private Fixture fixture() {
    String actor = "dep-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    var home =
        rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "朝阳路公寓", "", ""));
    var contract =
        rentals.createRentalContract(
            new CreateRentalContractCommand(
                ledger,
                actor,
                home.id(),
                "出租方",
                START,
                START.plusYears(1),
                250000,
                100000,
                RentalPaymentCycle.MONTHLY,
                null,
                ""));
    return new Fixture(ledger, actor, contract.id());
  }

  private void record(
      Fixture f, RentalDepositKind kind, long cents, LocalDate date, String accountId) {
    deposits.recordRentalDeposit(
        new RecordRentalDepositCommand(
            f.ledger(), f.actor(), f.contract(), kind, cents, date, accountId, ""));
  }

  private long balance(Fixture f, String accountId) {
    return accounts.listLedgerAccounts(f.ledger(), f.actor()).stream()
        .filter(row -> row.id().equals(accountId))
        .findFirst()
        .orElseThrow()
        .balanceCents();
  }

  private RentalDepositPageResult list(Fixture f) {
    return deposits.listRentalDeposits(f.ledger(), f.actor(), f.contract(), 0);
  }

  private record Fixture(String ledger, String actor, String contract) {}
}
