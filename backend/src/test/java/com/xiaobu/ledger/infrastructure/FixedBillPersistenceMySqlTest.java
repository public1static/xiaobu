package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.CreateFixedBillCommand;
import com.xiaobu.ledger.application.FixedBillApplicationService;
import com.xiaobu.ledger.application.PayFixedBillCommand;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
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
class FixedBillPersistenceMySqlTest {
  @Autowired private FixedBillApplicationService bills;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void paginatesServerSideAndOnlyPaysOneTransactionPerPeriod() throws Exception {
    String actor = "bill-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    var firstDue = LocalDate.of(2025, 1, 31);
    for (int i = 0; i < 21; i++) {
      bills.createFixedBill(
          new CreateFixedBillCommand(ledger, actor, "宽带 " + i, firstDue, 9900, "DAILY", null));
    }

    var february = YearMonth.of(2025, 2);
    var firstPage = bills.listFixedBillPeriods(ledger, actor, february, false, 0);
    var secondPage = bills.listFixedBillPeriods(ledger, actor, february, false, 1);
    assertEquals(21, firstPage.totalCount());
    assertEquals(20, firstPage.entries().size());
    assertEquals(1, secondPage.entries().size());
    var period = secondPage.entries().getFirst();
    assertEquals(february.atEndOfMonth(), period.dueOn());
    assertNotEquals(firstPage.entries().getLast().billId(), period.billId());
    assertEquals(
        0,
        bills.listFixedBillPeriods(ledger, actor, YearMonth.of(2024, 12), false, 0).totalCount());
    assertEquals(
        LocalDate.of(2025, 3, 31),
        bills
            .listFixedBillPeriods(ledger, actor, february.plusMonths(1), false, 0)
            .entries()
            .getFirst()
            .dueOn());
    assertThrows(
        FoundationException.class,
        () -> bills.listFixedBillPeriods(ledger, "not-a-member", february, false, 0));

    String reader = "reader-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO ledger_members (id, ledger_id, member_id, inviter_name) VALUES (?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        ledger,
        reader,
        "甲");
    assertEquals(21, bills.listFixedBillPeriods(ledger, reader, february, false, 0).totalCount());
    assertThrows(
        FoundationException.class,
        () ->
            bills.payFixedBill(
                new PayFixedBillCommand(
                    ledger,
                    reader,
                    period.billId(),
                    february,
                    LocalDate.of(2025, 2, 27),
                    9700,
                    null)));

    var payment =
        new PayFixedBillCommand(
            ledger, actor, period.billId(), february, LocalDate.of(2025, 2, 27), 9700, null);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var ready = new CyclicBarrier(2);
      var one =
          workers.submit(() -> {
            ready.await(5, TimeUnit.SECONDS);
            return bills.payFixedBill(payment);
          });
      var two =
          workers.submit(() -> {
            ready.await(5, TimeUnit.SECONDS);
            return bills.payFixedBill(payment);
          });
      assertEquals(
          one.get(20, TimeUnit.SECONDS).transactionId(),
          two.get(20, TimeUnit.SECONDS).transactionId());
    }

    var paid = bills.listFixedBillPeriods(ledger, actor, february, true, 0);
    assertEquals(1, paid.totalCount());
    assertEquals(9700, paid.entries().getFirst().paidAmountCents());
    assertEquals(20, bills.listFixedBillPeriods(ledger, actor, february, false, 0).totalCount());
    assertEquals(
        0, bills.listFixedBillPeriods(ledger, actor, february.plusMonths(1), true, 0).totalCount());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_transactions WHERE ledger_id = ?", Integer.class, ledger));
  }
}
