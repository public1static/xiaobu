package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.ChangeLedgerTransactionStateCommand;
import com.xiaobu.ledger.application.CreateFixedBillCommand;
import com.xiaobu.ledger.application.CreateRentalContractCommand;
import com.xiaobu.ledger.application.FixedBillApplicationService;
import com.xiaobu.ledger.application.GenerateRentalBillCommand;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.LinkFixedBillPaymentCommand;
import com.xiaobu.ledger.application.LinkRentalBillCommand;
import com.xiaobu.ledger.application.ListFixedBillCandidatesQuery;
import com.xiaobu.ledger.application.ListRentalBillPlansQuery;
import com.xiaobu.ledger.application.PayFixedBillCommand;
import com.xiaobu.ledger.application.RecordLedgerTransactionCommand;
import com.xiaobu.ledger.application.RentalApplicationService;
import com.xiaobu.ledger.application.SaveRentalHomeCommand;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
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
class RentalPersistenceMySqlTest {
  @Autowired private FixedBillApplicationService bills;
  @Autowired private RentalApplicationService rentals;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LedgerTransactionApplicationService transactions;

  @Test
  void generatesOnlyLeaseDueMonthsAndReusesSinglePaymentLifecycle() {
    String actor = "rent-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    String other = "book_" + UUID.randomUUID();
    String otherActor = "rent-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?), (?, ?)",
        ledger,
        actor,
        other,
        otherActor);
    var home =
        rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "朝阳路公寓", "测试地址", ""));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.saveRentalHome(
                new SaveRentalHomeCommand(ledger, actor, null, "朝阳路公寓 ", "", "")));
    var jan = YearMonth.of(2025, 1);
    var march = jan.plusMonths(2);
    var apr = jan.plusMonths(3);
    var july = jan.plusMonths(6);
    var start = jan.atDay(15);
    var charge = jan.atEndOfMonth();
    var beforeStart =
        new CreateRentalContractCommand(
            ledger,
            actor,
            home.id(),
            "测试房东",
            start,
            july.atDay(15),
            250000,
            500000,
            RentalPaymentCycle.QUARTERLY,
            jan.atDay(1),
            "");
    assertThrows(FoundationException.class, () -> rentals.createRentalContract(beforeStart));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.createRentalContract(
                new CreateRentalContractCommand(
                    other,
                    otherActor,
                    home.id(),
                    "测试房东",
                    start,
                    july.atDay(15),
                    250000,
                    0,
                    RentalPaymentCycle.QUARTERLY,
                    charge,
                    "")));
    var contract =
        rentals.createRentalContract(
            new CreateRentalContractCommand(
                ledger,
                actor,
                home.id(),
                "测试房东",
                start,
                july.atDay(15),
                250000,
                500000,
                RentalPaymentCycle.QUARTERLY,
                charge,
                ""));
    var otherHome =
        rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "知春路公寓", "", ""));
    var inclusive =
        rentals.createRentalContract(
            new CreateRentalContractCommand(
                ledger,
                actor,
                otherHome.id(),
                "测试房东",
                start,
                july.atEndOfMonth(),
                250000,
                0,
                RentalPaymentCycle.QUARTERLY,
                charge,
                ""));
    assertEquals(1, rentals.listRentalHomes(ledger, actor, 0).entries().getFirst().contractCount());
    assertEquals(1, rentals.listRentalContracts(ledger, actor, home.id(), 0).totalCount());
    assertEquals(0, rentals.listRentalContracts(other, otherActor, null, 0).totalCount());
    assertEquals(0, bills.listFixedBillPlans(ledger, actor, 0).totalCount());
    assertEquals(0, transactionCount(ledger));
    assertEquals(
        1,
        bills.listFixedBillPeriods(ledger, actor, jan, false, 0, contract.billId()).totalCount());
    assertEquals(0, bills.listFixedBillPeriods(ledger, actor, march, false, 0).totalCount());
    assertEquals(1, bills.listFixedBillPeriods(ledger, actor, july, false, 0).totalCount());
    assertEquals(
        inclusive.billId(),
        bills.listFixedBillPeriods(ledger, actor, july, false, 0).entries().getFirst().billId());
    var april = bills.listFixedBillPeriods(ledger, actor, apr, false, 0, contract.billId());
    assertEquals(1, april.totalCount());
    assertEquals(apr.atEndOfMonth(), april.entries().getFirst().dueOn());
    assertEquals(750000, april.entries().getFirst().amountCents());
    assertThrows(
        FoundationException.class,
        () ->
            bills.payFixedBill(
                new PayFixedBillCommand(
                    ledger, actor, contract.billId(), july, march.atDay(20), 750000, null)));
    assertThrows(
        FoundationException.class,
        () ->
            bills.listFixedBillCandidates(
                new ListFixedBillCandidatesQuery(ledger, actor, contract.billId(), march, "", 0)));
    var expense =
        transactions.recordLedgerTransaction(
            new RecordLedgerTransactionCommand(
                ledger,
                actor,
                TransactionDirection.EXPENSE,
                750000,
                march.atDay(20),
                "HOUSING",
                "房租预付",
                null));
    var linked =
        bills.linkFixedBillPayment(
            new LinkFixedBillPaymentCommand(
                ledger, actor, contract.billId(), apr, expense.id(), 0));
    assertEquals(expense.id(), linked.transactionId());
    assertEquals(1, transactionCount(ledger));
    assertEquals(
        1, bills.listFixedBillPeriods(ledger, actor, apr, true, 0, contract.billId()).totalCount());
    transactions.deleteLedgerTransaction(
        new ChangeLedgerTransactionStateCommand(ledger, actor, expense.id(), 0));
    assertEquals(
        1,
        bills.listFixedBillPeriods(ledger, actor, apr, false, 0, contract.billId()).totalCount());
    transactions.restoreLedgerTransaction(
        new ChangeLedgerTransactionStateCommand(ledger, actor, expense.id(), 1));
    assertEquals(
        expense.id(),
        bills
            .listFixedBillPeriods(ledger, actor, apr, true, 0, contract.billId())
            .entries()
            .getFirst()
            .transactionId());
  }

  @Test
  void paginatesBothRentalDirectories() {
    String actor = "rent-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    var home = rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "房屋", "", ""));
    var start = LocalDate.of(2025, 1, 1);
    for (int i = 0; i < 20; i++) {
      rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "房屋 " + i, "", ""));
    }
    for (int i = 0; i < 21; i++) {
      rentals.createRentalContract(
          new CreateRentalContractCommand(
              ledger,
              actor,
              home.id(),
              "测试 " + i,
              start,
              start.plusMonths(1),
              100000,
              0,
              RentalPaymentCycle.MONTHLY,
              null,
              ""));
    }
    assertEquals(21, rentals.listRentalHomes(ledger, actor, 0).totalCount());
    assertEquals(1, rentals.listRentalHomes(ledger, actor, 1).entries().size());
    assertEquals(21, rentals.listRentalContracts(ledger, actor, home.id(), 0).totalCount());
    assertEquals(20, rentals.listRentalContracts(ledger, actor, home.id(), 0).entries().size());
    assertEquals(1, rentals.listRentalContracts(ledger, actor, home.id(), 1).entries().size());
    assertEquals(
        0, bills.listFixedBillPeriods(ledger, actor, YearMonth.from(start), false, 0).totalCount());
  }

  @Test
  void setsUpRentLaterWithoutDuplicatingThePlan() {
    String actor = "rent-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    var home = rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "房屋", "", ""));
    var start = LocalDate.of(2025, 1, 31);
    var initial =
        rentals.createRentalContract(
            new CreateRentalContractCommand(
                ledger,
                actor,
                home.id(),
                "测试出租方",
                start,
                start.plusMonths(7),
                100000,
                0,
                RentalPaymentCycle.SEMIANNUAL,
                null,
                ""));
    var command = new GenerateRentalBillCommand(ledger, actor, initial.id(), start);
    var updated = rentals.generateRentalBill(command);
    assertEquals(updated, rentals.generateRentalBill(command));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.generateRentalBill(
                new GenerateRentalBillCommand(ledger, actor, initial.id(), start.plusDays(1))));
    assertEquals(
        updated, rentals.listRentalContracts(ledger, actor, home.id(), 0).entries().getFirst());
    assertEquals(
        1,
        bills
            .listFixedBillPeriods(ledger, actor, YearMonth.from(start).plusMonths(6), false, 0)
            .totalCount());
    assertEquals(0, transactionCount(ledger));
  }

  @Test
  void adoptsExistingHousingPlanWithoutReplacingPaymentsOrLeavingDuplicateDuePeriods() {
    String actor = "rent-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    var jan = YearMonth.of(2025, 1);
    var feb = jan.plusMonths(1);
    var march = jan.plusMonths(2);
    var april = jan.plusMonths(3);
    var old =
        bills.createFixedBill(
            new CreateFixedBillCommand(
                ledger, actor, "已有房租", jan.atEndOfMonth(), 220000, "HOUSING", null));
    var power =
        bills.createFixedBill(
            new CreateFixedBillCommand(
                ledger, actor, "水费", jan.atEndOfMonth(), 9900, "DAILY", null));
    var historical =
        bills.payFixedBill(
            new PayFixedBillCommand(ledger, actor, old.billId(), jan, jan.atDay(20), 220000, null));
    var inLease =
        bills.payFixedBill(
            new PayFixedBillCommand(ledger, actor, old.billId(), feb, jan.atDay(25), 220000, null));
    var prepaid =
        bills.payFixedBill(
            new PayFixedBillCommand(
                ledger, actor, old.billId(), april, march.atDay(20), 220000, null));
    var start = feb.atEndOfMonth();
    var end = april.atDay(15);
    var query =
        new ListRentalBillPlansQuery(ledger, actor, RentalPaymentCycle.MONTHLY, start, end, 0);
    assertEquals(1, rentals.listRentalBillPlans(query).totalCount());
    assertEquals(start, rentals.listRentalBillPlans(query).entries().getFirst().firstDueOn());
    assertEquals(
        0,
        rentals
            .listRentalBillPlans(
                new ListRentalBillPlansQuery(
                    ledger, actor, RentalPaymentCycle.QUARTERLY, start, end, 0))
            .totalCount());
    assertEquals(
        0,
        rentals
            .listRentalBillPlans(
                new ListRentalBillPlansQuery(
                    ledger, actor, RentalPaymentCycle.MONTHLY, april.atDay(1), end, 0))
            .totalCount());
    var home = rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "房屋", "", ""));
    var contract =
        rentals.createRentalContract(
            new CreateRentalContractCommand(
                ledger,
                actor,
                home.id(),
                "出租方",
                start,
                end,
                250000,
                0,
                RentalPaymentCycle.MONTHLY,
                null,
                ""));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.linkRentalBill(
                new LinkRentalBillCommand(ledger, actor, contract.id(), power.billId())));
    var link = new LinkRentalBillCommand(ledger, actor, contract.id(), old.billId());
    var attached = rentals.linkRentalBill(link);
    assertEquals(old.billId(), attached.billId());
    assertEquals(start, attached.firstDueOn());
    assertEquals(31, attached.dueDayOfMonth());
    assertEquals(attached, rentals.linkRentalBill(link));
    assertEquals(
        attached, rentals.listRentalContracts(ledger, actor, home.id(), 0).entries().getFirst());
    assertEquals(0, rentals.listRentalBillPlans(query).totalCount());
    assertEquals(3, transactionCount(ledger));
    assertEquals(1, bills.listFixedBillPlans(ledger, actor, 0).totalCount());
    assertEquals(
        250000,
        bills
            .listFixedBillPeriods(ledger, actor, march, false, 0, old.billId())
            .entries()
            .getFirst()
            .amountCents());
    assertEquals(
        220000L,
        bills
            .listFixedBillPeriods(ledger, actor, feb, true, 0, old.billId())
            .entries()
            .getFirst()
            .paidAmountCents());
    assertEquals(
        inLease.transactionId(),
        bills
            .listFixedBillPeriods(ledger, actor, feb, true, 0, old.billId())
            .entries()
            .getFirst()
            .transactionId());
    assertEquals(
        0, bills.listFixedBillPeriods(ledger, actor, april, true, 0, old.billId()).totalCount());
    assertEquals(
        prepaid.transactionId(),
        bills
            .listFixedBillPeriods(ledger, actor, april, true, 0)
            .entries()
            .getFirst()
            .transactionId());
    assertEquals(
        historical.transactionId(),
        bills
            .listFixedBillPeriods(ledger, actor, jan, true, 0)
            .entries()
            .getFirst()
            .transactionId());
    assertEquals(
        0,
        bills
            .listFixedBillPeriods(ledger, actor, april.plusMonths(1), false, 0, old.billId())
            .totalCount());
    assertThrows(
        FoundationException.class,
        () ->
            bills.payFixedBill(
                new PayFixedBillCommand(
                    ledger,
                    actor,
                    old.billId(),
                    april.plusMonths(1),
                    april.atDay(1),
                    250000,
                    null)));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.createRentalContract(
                new CreateRentalContractCommand(
                    ledger,
                    actor,
                    home.id(),
                    "另一份",
                    start,
                    end,
                    250000,
                    0,
                    RentalPaymentCycle.MONTHLY,
                    start,
                    "")));
    var otherHome =
        rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "另一套房", "", ""));
    assertThrows(
        FoundationException.class,
        () ->
            rentals.createRentalContract(
                new CreateRentalContractCommand(
                    ledger,
                    actor,
                    otherHome.id(),
                    "另一个出租方",
                    start,
                    end,
                    250000,
                    0,
                    RentalPaymentCycle.MONTHLY,
                    null,
                    "",
                    old.billId())));
  }

  @Test
  void candidatePagingAndCreatingWithAPlanUseTheSameLeaseWindow() {
    String actor = "rent-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    var day = LocalDate.of(2025, 2, 28);
    var anchor = LocalDate.of(2025, 1, 31);
    for (int i = 0; i < 21; i++) {
      bills.createFixedBill(
          new CreateFixedBillCommand(ledger, actor, "候选房租 " + i, anchor, 7000, "HOUSING", null));
    }
    var firstPage =
        rentals.listRentalBillPlans(
            new ListRentalBillPlansQuery(ledger, actor, RentalPaymentCycle.MONTHLY, day, day, 0));
    var nextPage =
        rentals.listRentalBillPlans(
            new ListRentalBillPlansQuery(ledger, actor, RentalPaymentCycle.MONTHLY, day, day, 1));
    assertEquals(21, firstPage.totalCount());
    assertEquals(20, firstPage.entries().size());
    assertEquals(21, nextPage.totalCount());
    var existing = nextPage.entries().getFirst();
    assertEquals(day, existing.firstDueOn());
    var home = rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "房屋", "", ""));
    var contract =
        rentals.createRentalContract(
            new CreateRentalContractCommand(
                ledger,
                actor,
                home.id(),
                "出租方",
                day,
                day,
                8000,
                0,
                RentalPaymentCycle.MONTHLY,
                null,
                "",
                existing.billId()));
    assertEquals(existing.billId(), contract.billId());
    assertEquals(day, contract.firstDueOn());
    assertEquals(
        20,
        rentals
            .listRentalBillPlans(
                new ListRentalBillPlansQuery(
                    ledger, actor, RentalPaymentCycle.MONTHLY, day, day, 0))
            .totalCount());
    assertEquals(0, transactionCount(ledger));
  }

  private int transactionCount(String ledger) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM ledger_transactions WHERE ledger_id = ?", Integer.class, ledger);
  }
}
