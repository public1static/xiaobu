package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.CreateFixedBillCommand;
import com.xiaobu.ledger.application.CreateRentalContractCommand;
import com.xiaobu.ledger.application.FixedBillApplicationService;
import com.xiaobu.ledger.application.LedgerTodoApplicationService;
import com.xiaobu.ledger.application.LedgerTodoQueryRepository;
import com.xiaobu.ledger.application.LedgerTodoResult;
import com.xiaobu.ledger.application.PayFixedBillCommand;
import com.xiaobu.ledger.application.RentalApplicationService;
import com.xiaobu.ledger.application.SaveRentalHomeCommand;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
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
class LedgerTodoPersistenceMySqlTest {
  private static final LocalDate TODAY = LocalDate.of(2025, 2, 28);
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LedgerTodoQueryRepository repository;
  @Autowired private LedgerTodoApplicationService todos;
  @Autowired private FixedBillApplicationService bills;
  @Autowired private RentalApplicationService rentals;

  @Test
  void aggregatesOccurrencesAcrossSourcesRetainingPastBillsAndOnlyTheFirstFuturePeriod() {
    var f = fixture();
    var bill =
        bills.createFixedBill(
            new CreateFixedBillCommand(
                f.ledger(), f.actor(), "话费", LocalDate.of(2025, 1, 31), 10000, "DAILY", null));
    var home =
        rentals.saveRentalHome(
            new SaveRentalHomeCommand(f.ledger(), f.actor(), null, "住房", "", ""));
    var contract =
        rentals.createRentalContract(
            new CreateRentalContractCommand(
                f.ledger(),
                f.actor(),
                home.id(),
                "周先生",
                TODAY,
                TODAY,
                250000,
                0,
                RentalPaymentCycle.MONTHLY,
                TODAY,
                ""));
    var rows = repository.listLedgerTodos(f.ledger(), TODAY, false, 0);
    assertEquals(4, rows.pendingCount());
    assertEquals(4, repository.countPendingLedgerTodos(f.ledger(), TODAY));
    assertEquals(
        List.of("2025-01", "2025-02", "2025-02", "2025-02-28", "2025-03"),
        rows.entries().stream().map(LedgerTodoResult::occurrence).toList());
    assertEquals(
        1L, rows.entries().stream().filter(r -> r.sourceId().equals(contract.billId())).count());
    assertTrue(
        rows.entries().stream()
            .anyMatch(
                r -> r.source().equals("RENTAL_EXPIRY") && r.sourceId().equals(contract.id())));
    assertTrue(
        rows.entries().stream()
            .anyMatch(r ->
                r.sourceId().equals(bill.billId())
                    && r.dueOn().equals(TODAY)
                    && r.status().equals("PENDING")));
    var paid =
        bills.payFixedBill(
            new PayFixedBillCommand(
                f.ledger(), f.actor(), bill.billId(), YearMonth.from(TODAY), TODAY, 9000, null));
    assertEquals(3, repository.countPendingLedgerTodos(f.ledger(), TODAY));
    var complete = repository.listLedgerTodos(f.ledger(), TODAY, true, 0);
    assertEquals(1, complete.totalCount());
    assertEquals(9000, complete.entries().getFirst().amountCents());
    assertEquals(
        paid.transactionId(),
        bills
            .getFixedBillPeriod(f.ledger(), f.actor(), bill.billId(), YearMonth.from(TODAY))
            .transactionId());
    assertThrows(
        FoundationException.class, () -> todos.listLedgerTodos(f.ledger(), "outsider", false, 0));
  }

  @Test
  void paginatesGloballyWithStableTieBreakerAndKeepsStoppedMonthsOut() {
    var f = fixture();
    for (int i = 0; i < 21; i++) {
      bills.createFixedBill(
          new CreateFixedBillCommand(
              f.ledger(), f.actor(), "水电 " + i, TODAY, 1000 + i, "DAILY", null));
    }
    var first = repository.listLedgerTodos(f.ledger(), TODAY, false, 0);
    var second = repository.listLedgerTodos(f.ledger(), TODAY, false, 1);
    assertEquals(42, first.totalCount());
    assertEquals(21, first.pendingCount());
    assertEquals(20, first.entries().size());
    assertEquals(20, second.entries().size());
    assertEquals("PENDING", second.entries().getFirst().status());
    assertEquals("PLANNED", second.entries().getLast().status());
    assertFalse(new HashSet<>(first.entries()).removeAll(second.entries()));
    assertEquals(2, repository.listLedgerTodos(f.ledger(), TODAY, false, 2).entries().size());
    jdbc.update("UPDATE fixed_bills SET stop_month = '2025-03-01' WHERE ledger_id = ?", f.ledger());
    assertEquals(21, repository.listLedgerTodos(f.ledger(), TODAY, false, 0).totalCount());
  }

  private Fixture fixture() {
    String actor = "todo-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    return new Fixture(ledger, actor);
  }

  private record Fixture(String ledger, String actor) {}
}
