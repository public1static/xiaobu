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
import com.xiaobu.ledger.application.LedgerManualReminderApplicationService;
import com.xiaobu.ledger.application.LedgerTodoApplicationService;
import com.xiaobu.ledger.application.LedgerTodoQuery;
import com.xiaobu.ledger.application.LedgerTodoQueryRepository;
import com.xiaobu.ledger.application.LedgerTodoResult;
import com.xiaobu.ledger.application.PayFixedBillCommand;
import com.xiaobu.ledger.application.RentalApplicationService;
import com.xiaobu.ledger.application.SaveLedgerManualReminderCommand;
import com.xiaobu.ledger.application.SaveRentalHomeCommand;
import com.xiaobu.ledger.domain.ManualReminderFrequency;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;
import java.time.LocalTime;
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
  @Autowired private LedgerManualReminderApplicationService reminders;
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
    var rows = repository.listLedgerTodos(query(f), false, 0);
    assertEquals(4, rows.pendingCount());
    assertEquals(4, repository.countPendingLedgerTodos(query(f)));
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
    assertEquals(3, repository.countPendingLedgerTodos(query(f)));
    var complete = repository.listLedgerTodos(query(f), true, 0);
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
    var first = repository.listLedgerTodos(query(f), false, 0);
    var second = repository.listLedgerTodos(query(f), false, 1);
    assertEquals(42, first.totalCount());
    assertEquals(21, first.pendingCount());
    assertEquals(20, first.entries().size());
    assertEquals(20, second.entries().size());
    assertEquals("PENDING", second.entries().getFirst().status());
    assertEquals("PLANNED", second.entries().getLast().status());
    assertFalse(new HashSet<>(first.entries()).removeAll(second.entries()));
    assertEquals(2, repository.listLedgerTodos(query(f), false, 2).entries().size());
    jdbc.update("UPDATE fixed_bills SET stop_month = '2025-03-01' WHERE ledger_id = ?", f.ledger());
    assertEquals(21, repository.listLedgerTodos(query(f), false, 0).totalCount());
  }

  @Test
  void activatesAtTheScheduledBeijingMinuteAndMovesBetweenQueuesWithoutCreatingTransactions() {
    var f = fixture();
    var due = TODAY.plusDays(3);
    var time = LocalTime.of(9, 30);
    var reminder =
        reminders.saveLedgerManualReminder(
            new SaveLedgerManualReminderCommand(
                f.ledger(),
                f.actor(),
                null,
                "联系房东",
                due,
                time,
                3,
                ManualReminderFrequency.ONCE,
                null));
    assertEquals(TODAY.atTime(time), reminder.remindAt());
    assertEquals(
        "2025-02-28 09:30:00",
        jdbc.queryForObject(
            "SELECT CAST(remind_at AS CHAR) FROM ledger_manual_reminders WHERE id = ?",
            String.class,
            reminder.id()));
    assertEquals(
        reminder,
        reminders.getLedgerManualReminder(f.ledger(), f.actor(), reminder.id(), null).reminder());
    var before = new LedgerTodoQuery(f.ledger(), f.actor(), reminder.remindAt().minusSeconds(1));
    var at = new LedgerTodoQuery(f.ledger(), f.actor(), reminder.remindAt());
    var planned = repository.listLedgerTodos(before, false, 0);
    assertEquals(1, planned.totalCount());
    assertEquals(0, planned.pendingCount());
    assertEquals("PLANNED", planned.entries().getFirst().status());
    assertEquals(0, repository.countPendingLedgerTodos(before));
    assertEquals(1, repository.countPendingLedgerTodos(at));
    assertEquals("PENDING", repository.listLedgerTodos(at, false, 0).entries().getFirst().status());

    var completed =
        reminders.setLedgerManualReminderCompletion(
            f.ledger(), f.actor(), reminder.id(), reminder.revision(), true);
    assertThrows(
        FoundationException.class,
        () ->
            reminders.setLedgerManualReminderCompletion(
                f.ledger(), f.actor(), reminder.id(), reminder.revision(), true));
    assertEquals(0, repository.listLedgerTodos(at, false, 0).totalCount());
    assertEquals(
        "COMPLETED", repository.listLedgerTodos(at, true, 0).entries().getFirst().status());
    assertThrows(
        FoundationException.class,
        () ->
            reminders.saveLedgerManualReminder(
                new SaveLedgerManualReminderCommand(
                    f.ledger(),
                    f.actor(),
                    reminder.id(),
                    "修改",
                    due,
                    time,
                    0,
                    ManualReminderFrequency.ONCE,
                    completed.revision())));
    var restored =
        reminders.setLedgerManualReminderCompletion(
            f.ledger(), f.actor(), reminder.id(), reminder.revision(), false);
    assertEquals(1, repository.countPendingLedgerTodos(at));
    var revised =
        reminders.saveLedgerManualReminder(
            new SaveLedgerManualReminderCommand(
                f.ledger(),
                f.actor(),
                reminder.id(),
                " 更新 ",
                due,
                time,
                1,
                ManualReminderFrequency.ONCE,
                restored.revision()));
    assertEquals(reminder.id(), revised.id());
    assertEquals("更新", revised.title());
    assertEquals(0, repository.countPendingLedgerTodos(at));
    assertEquals(0, repository.listLedgerTodos(at, true, 0).totalCount());
    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_transactions WHERE ledger_id = ?",
            Long.class,
            f.ledger()));
  }

  @Test
  void isolatesManualRemindersInsideSharedLedgerAndValidatesSchedule() {
    var f = fixture();
    var other = fixture();
    var reminder =
        reminders.saveLedgerManualReminder(
            new SaveLedgerManualReminderCommand(
                f.ledger(),
                f.actor(),
                null,
                "本人提醒",
                TODAY,
                LocalTime.MIDNIGHT,
                0,
                ManualReminderFrequency.ONCE,
                null));
    var otherActorOnSameLedger =
        new LedgerTodoQuery(f.ledger(), other.actor(), TODAY.atStartOfDay());
    assertEquals(0, repository.listLedgerTodos(otherActorOnSameLedger, false, 0).totalCount());
    assertEquals(0, repository.countPendingLedgerTodos(otherActorOnSameLedger));
    assertThrows(
        FoundationException.class,
        () ->
            reminders.getLedgerManualReminder(other.ledger(), other.actor(), reminder.id(), null));
    assertThrows(
        FoundationException.class,
        () ->
            reminders.setLedgerManualReminderCompletion(
                f.ledger(), other.actor(), reminder.id(), reminder.revision(), true));
    assertThrows(
        FoundationException.class,
        () ->
            reminders.saveLedgerManualReminder(
                new SaveLedgerManualReminderCommand(
                    f.ledger(),
                    f.actor(),
                    null,
                    "提前 2 天",
                    TODAY,
                    LocalTime.NOON,
                    2,
                    ManualReminderFrequency.ONCE,
                    null)));
    assertThrows(
        FoundationException.class,
        () ->
            reminders.saveLedgerManualReminder(
                new SaveLedgerManualReminderCommand(
                    f.ledger(),
                    f.actor(),
                    null,
                    "秒数",
                    TODAY,
                    LocalTime.of(10, 0, 1),
                    0,
                    ManualReminderFrequency.ONCE,
                    null)));
    assertEquals(1, repository.listLedgerTodos(query(f), false, 0).totalCount());
  }

  @Test
  void onlyCompletesTheSelectedOccurrenceAndRetainsTheOriginalMonthEndAnchor() {
    var f = fixture();
    var date = LocalDate.of(2027, 1, 31);
    var time = LocalTime.of(9, 30);
    var series =
        reminders.saveLedgerManualReminder(
            new SaveLedgerManualReminderCommand(
                f.ledger(),
                f.actor(),
                null,
                "月末核对",
                date,
                time,
                0,
                ManualReminderFrequency.MONTHLY,
                null));
    var at = new LedgerTodoQuery(f.ledger(), f.actor(), series.remindAt());
    assertEquals(
        Long.toString(series.revision()),
        repository.listLedgerTodos(at, false, 0).entries().getFirst().occurrence());

    var february =
        reminders.setLedgerManualReminderCompletion(
            f.ledger(), f.actor(), series.id(), series.revision(), true);
    assertEquals(LocalDate.of(2027, 2, 28), february.dueOn());
    assertEquals(0, repository.countPendingLedgerTodos(at));
    assertThrows(
        FoundationException.class,
        () ->
            reminders.setLedgerManualReminderCompletion(
                f.ledger(), f.actor(), series.id(), series.revision(), true));
    assertEquals(1, repository.listLedgerTodos(at, true, 0).totalCount());
    assertEquals(
        february,
        reminders.getLedgerManualReminder(f.ledger(), f.actor(), series.id(), null).reminder());

    var january =
        reminders.getLedgerManualReminder(f.ledger(), f.actor(), series.id(), series.revision());
    assertTrue(january.canUndo());
    assertEquals(date, january.reminder().dueOn());
    var restored =
        reminders.setLedgerManualReminderCompletion(
            f.ledger(), f.actor(), series.id(), series.revision(), false);
    assertEquals(date, restored.dueOn());
    assertEquals(0, repository.listLedgerTodos(at, true, 0).totalCount());
    assertThrows(
        FoundationException.class,
        () ->
            reminders.setLedgerManualReminderCompletion(
                f.ledger(), f.actor(), series.id(), series.revision(), true));
    var februaryAgain =
        reminders.setLedgerManualReminderCompletion(
            f.ledger(), f.actor(), series.id(), restored.revision(), true);
    assertEquals(february.dueOn(), februaryAgain.dueOn());
    var edited =
        reminders.saveLedgerManualReminder(
            new SaveLedgerManualReminderCommand(
                f.ledger(),
                f.actor(),
                series.id(),
                "新标题",
                februaryAgain.dueOn(),
                LocalTime.of(10, 30),
                1,
                ManualReminderFrequency.MONTHLY,
                februaryAgain.revision()));
    assertEquals(date, edited.anchorOn());
    var march =
        reminders.setLedgerManualReminderCompletion(
            f.ledger(), f.actor(), series.id(), edited.revision(), true);
    assertEquals(LocalDate.of(2027, 3, 31), march.dueOn());
    assertEquals(LocalDate.of(2027, 3, 30).atTime(10, 30), march.remindAt());
    assertEquals(
        "月末核对",
        reminders
            .getLedgerManualReminder(f.ledger(), f.actor(), series.id(), restored.revision())
            .reminder()
            .title());
    assertFalse(
        reminders
            .getLedgerManualReminder(f.ledger(), f.actor(), series.id(), restored.revision())
            .canUndo());
    assertThrows(
        FoundationException.class,
        () ->
            reminders.setLedgerManualReminderCompletion(
                f.ledger(), f.actor(), series.id(), restored.revision(), false));
    reminders.stopLedgerManualReminder(f.ledger(), f.actor(), series.id(), march.revision());
    assertEquals(0, repository.listLedgerTodos(at, false, 0).totalCount());
    assertEquals(2, repository.listLedgerTodos(at, true, 0).totalCount());
  }

  private Fixture fixture() {
    String actor = "todo-" + UUID.randomUUID();
    String ledger = "book_" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    return new Fixture(ledger, actor);
  }

  private LedgerTodoQuery query(Fixture fixture) {
    return new LedgerTodoQuery(fixture.ledger(), fixture.actor(), TODAY.atStartOfDay());
  }

  private record Fixture(String ledger, String actor) {}
}
