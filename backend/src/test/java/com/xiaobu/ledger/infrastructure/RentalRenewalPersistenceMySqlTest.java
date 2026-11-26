package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.CompleteRentalExpiryReminderCommand;
import com.xiaobu.ledger.application.CreateRentalContractCommand;
import com.xiaobu.ledger.application.FixedBillApplicationService;
import com.xiaobu.ledger.application.PayFixedBillCommand;
import com.xiaobu.ledger.application.RecordRentalDepositCommand;
import com.xiaobu.ledger.application.RenewRentalContractCommand;
import com.xiaobu.ledger.application.RentalApplicationService;
import com.xiaobu.ledger.application.RentalContractResult;
import com.xiaobu.ledger.application.RentalDepositApplicationService;
import com.xiaobu.ledger.application.RentalExpiryReminderApplicationService;
import com.xiaobu.ledger.application.SaveRentalHomeCommand;
import com.xiaobu.ledger.domain.RentalDepositKind;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class RentalRenewalPersistenceMySqlTest {
  private static final LocalDate START = LocalDate.of(2025, 1, 1);
  private static final LocalDate END = LocalDate.of(2025, 1, 31);

  @Autowired private JdbcTemplate jdbc;
  @Autowired private RentalApplicationService rentals;
  @Autowired private FixedBillApplicationService bills;
  @Autowired private RentalDepositApplicationService deposits;
  @Autowired private RentalExpiryReminderApplicationService reminders;

  @Test
  void linkedRenewalLeavesPreviousContractPaymentsDepositAndHandledReminderUntouched() {
    var f = fixture();
    var old = create(f, START, END, START);
    var payment =
        bills.payFixedBill(
            new PayFixedBillCommand(
                f.ledger(), f.actor(), old.billId(), YearMonth.from(START), START, 250000, null));
    deposits.recordRentalDeposit(
        new RecordRentalDepositCommand(
            f.ledger(), f.actor(), old.id(), RentalDepositKind.PAY, 100000, START, null, ""));
    reminders.completeRentalExpiryReminder(
        new CompleteRentalExpiryReminderCommand(f.ledger(), f.actor(), old.id(), END));
    var nextStart = END.plusDays(1);
    var next =
        rentals.renewRentalContract(
            renewal(f, old, END, nextStart, nextStart.plusMonths(2), nextStart));
    assertEquals(old.id(), next.previousContractId());
    assertEquals(old.homeId(), next.homeId());
    assertNotEquals(old.billId(), next.billId());
    assertEquals(0, next.depositCents());
    assertNull(next.handledEnd());
    assertEquals(
        next.id(), rentals.getRentalContract(f.ledger(), f.actor(), old.id()).nextContractId());
    var untouched = rentals.getRentalContract(f.ledger(), f.actor(), old.id());
    assertEquals(250000, untouched.monthlyRentCents());
    assertEquals(END, untouched.endsOn());
    assertEquals(END, untouched.handledEnd());
    assertEquals(
        payment.transactionId(),
        bills
            .listFixedBillPeriods(
                f.ledger(), f.actor(), YearMonth.from(START), true, 0, old.billId())
            .entries()
            .getFirst()
            .transactionId());
    assertEquals(
        1,
        rentals
            .listPendingRentalPeriods(f.ledger(), f.actor(), YearMonth.from(nextStart), 0)
            .totalCount());
    assertEquals(
        next.billId(),
        rentals
            .listPendingRentalPeriods(f.ledger(), f.actor(), YearMonth.from(nextStart), 0)
            .entries()
            .getFirst()
            .billId());
    assertEquals(
        100000, deposits.listRentalDeposits(f.ledger(), f.actor(), old.id(), 0).heldCents());
    assertEquals(0, deposits.listRentalDeposits(f.ledger(), f.actor(), next.id(), 0).heldCents());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_transactions WHERE ledger_id = ?",
            Integer.class,
            f.ledger()));
    assertThrows(
        FoundationException.class,
        () -> create(f, nextStart.plusDays(1), nextStart.plusDays(5), null));
  }

  @Test
  void rejectsStaleCrossLedgerAndOverlappingRenewalsWithoutLeavingANewPlan() {
    var f = fixture();
    var other = fixture();
    var old = create(f, START, END, null);
    var nextStart = END.plusDays(1);
    var later = create(f, nextStart.plusDays(12), nextStart.plusDays(30), null);
    assertThrows(
        FoundationException.class,
        () ->
            rentals.renewRentalContract(
                renewal(f, old, END.minusDays(1), nextStart, nextStart.plusDays(7), nextStart)));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.renewRentalContract(
                renewal(f, old, END, END, nextStart.plusDays(7), nextStart)));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.renewRentalContract(
                renewal(other, old, END, nextStart, nextStart.plusDays(7), nextStart)));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.renewRentalContract(
                renewal(f, old, END, nextStart, later.startsOn(), nextStart)));
    assertEquals(2, rentals.listRentalContracts(f.ledger(), f.actor(), f.home(), 0).totalCount());
    assertEquals(
        0,
        rentals
            .listPendingRentalPeriods(f.ledger(), f.actor(), YearMonth.from(nextStart), 0)
            .totalCount());
    assertNull(rentals.getRentalContract(f.ledger(), f.actor(), old.id()).nextContractId());
  }

  @Test
  void allowsOnlyOneSuccessorEvenWhenTwoRequestsArriveTogether() throws Exception {
    var f = fixture();
    var old = create(f, START, END, null);
    var first = END.plusDays(1);
    var command = renewal(f, old, END, first, first.plusMonths(1), null);
    var barrier = new CyclicBarrier(2);
    try (var workers = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> renew =
          () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
              rentals.renewRentalContract(command);
              return true;
            } catch (FoundationException alreadyRenewed) {
              return false;
            }
          };
      var a = workers.submit(renew);
      var b = workers.submit(renew);
      assertNotEquals(a.get(12, TimeUnit.SECONDS), b.get(12, TimeUnit.SECONDS));
    }
    assertEquals(2, rentals.listRentalContracts(f.ledger(), f.actor(), f.home(), 0).totalCount());
  }

  private Fixture fixture() {
    String ledger = "book_" + UUID.randomUUID();
    String actor = "renew-" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    var home =
        rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "朝阳路公寓", "", ""));
    return new Fixture(ledger, actor, home.id());
  }

  private RentalContractResult create(
      Fixture f, LocalDate startsOn, LocalDate endsOn, LocalDate firstDueOn) {
    return rentals.createRentalContract(
        new CreateRentalContractCommand(
            f.ledger(),
            f.actor(),
            f.home(),
            "出租方",
            startsOn,
            endsOn,
            250000,
            100000,
            RentalPaymentCycle.MONTHLY,
            firstDueOn,
            ""));
  }

  private RenewRentalContractCommand renewal(
      Fixture f,
      RentalContractResult source,
      LocalDate expectEnd,
      LocalDate startsOn,
      LocalDate endsOn,
      LocalDate firstDueOn) {
    return new RenewRentalContractCommand(
        f.ledger(),
        f.actor(),
        source.id(),
        expectEnd,
        "出租方",
        startsOn,
        endsOn,
        280000,
        0,
        RentalPaymentCycle.MONTHLY,
        firstDueOn,
        "",
        null,
        15);
  }

  private record Fixture(String ledger, String actor, String home) {}
}
