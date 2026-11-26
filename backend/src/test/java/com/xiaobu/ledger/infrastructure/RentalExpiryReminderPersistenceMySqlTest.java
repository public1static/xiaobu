package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.ledger.application.CompleteRentalExpiryReminderCommand;
import com.xiaobu.ledger.application.ConfigureRentalExpiryReminderCommand;
import com.xiaobu.ledger.application.CreateRentalContractCommand;
import com.xiaobu.ledger.application.RentalApplicationService;
import com.xiaobu.ledger.application.RentalContractResult;
import com.xiaobu.ledger.application.RentalExpiryReminderApplicationService;
import com.xiaobu.ledger.application.SaveRentalHomeCommand;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;
import java.time.ZoneId;
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
class RentalExpiryReminderPersistenceMySqlTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RentalExpiryReminderApplicationService reminders;
  @Autowired private RentalApplicationService rentals;

  @Test
  void prioritizesDueItemsAndBindsCompletionToTheActualExpiryDate() {
    var f = fixture();
    var other = fixture();
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    var planned = create(f, "短提前", today.plusDays(8), 7);
    var due = create(f, "提醒日边界", today.plusDays(15), 15);
    var disabled = create(f, "关闭提前", today, 0);
    var defaults = create(f, "默认提醒", today.plusDays(31), null);
    var past = create(f, "逾期", today.minusDays(3), 0);
    assertEquals(30, defaults.remindDays());
    var result = reminders.listRentalExpiryReminders(f.ledger(), f.actor(), false, 0);
    assertEquals(5, result.totalCount());
    assertEquals(3, result.pendingCount());
    assertEquals(3, reminders.countRentalExpiryReminders(f.ledger(), f.actor()));
    assertEquals(past.id(), result.entries().get(0).contractId());
    assertEquals(disabled.id(), result.entries().get(1).contractId());
    assertEquals(due.id(), result.entries().get(2).contractId());
    assertEquals(planned.id(), result.entries().get(3).contractId());
    assertFalse(result.entries().getLast().isPending(today));

    assertThrows(
        FoundationException.class,
        () ->
            reminders.completeRentalExpiryReminder(
                new CompleteRentalExpiryReminderCommand(
                    f.ledger(), f.actor(), planned.id(), planned.endsOn())));
    assertThrows(
        FoundationException.class,
        () ->
            reminders.completeRentalExpiryReminder(
                new CompleteRentalExpiryReminderCommand(f.ledger(), f.actor(), due.id(), today)));
    assertThrows(
        FoundationException.class,
        () ->
            reminders.configureRentalExpiryReminder(
                new ConfigureRentalExpiryReminderCommand(f.ledger(), f.actor(), due.id(), 3)));
    assertThrows(
        FoundationException.class,
        () ->
            reminders.configureRentalExpiryReminder(
                new ConfigureRentalExpiryReminderCommand(
                    other.ledger(), other.actor(), due.id(), 0)));
    assertThrows(
        FoundationException.class,
        () -> rentals.getRentalContract(other.ledger(), other.actor(), due.id()));

    var command =
        new CompleteRentalExpiryReminderCommand(f.ledger(), f.actor(), due.id(), due.endsOn());
    assertEquals(due.endsOn(), reminders.completeRentalExpiryReminder(command).handledEnd());
    assertEquals(due.endsOn(), reminders.completeRentalExpiryReminder(command).handledEnd());
    assertEquals(2, reminders.countRentalExpiryReminders(f.ledger(), f.actor()));
    var done = reminders.listRentalExpiryReminders(f.ledger(), f.actor(), true, 0);
    assertEquals(1, done.totalCount());
    assertEquals(due.id(), done.entries().getFirst().contractId());
    assertFalse(done.entries().getFirst().isPending(today));

    var configured =
        reminders.configureRentalExpiryReminder(
            new ConfigureRentalExpiryReminderCommand(f.ledger(), f.actor(), planned.id(), 15));
    assertEquals(15, configured.remindDays());
    assertEquals(3, reminders.countRentalExpiryReminders(f.ledger(), f.actor()));
    assertEquals(15, rentals.getRentalContract(f.ledger(), f.actor(), planned.id()).remindDays());
    reminders.configureRentalExpiryReminder(
        new ConfigureRentalExpiryReminderCommand(f.ledger(), f.actor(), due.id(), 30));
    assertEquals(
        1, reminders.listRentalExpiryReminders(f.ledger(), f.actor(), true, 0).totalCount());
    assertEquals(3, reminders.countRentalExpiryReminders(f.ledger(), f.actor()));
    assertEquals(0, reminders.countRentalExpiryReminders(other.ledger(), other.actor()));
  }

  @Test
  void paginatesPlannedAndPendingTogetherWithoutChangingTheBellCount() {
    var f = fixture();
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    for (int i = 0; i < 21; i++) {
      create(f, "合同 " + i, today.plusDays(i), 7);
    }
    var first = reminders.listRentalExpiryReminders(f.ledger(), f.actor(), false, 0);
    var second = reminders.listRentalExpiryReminders(f.ledger(), f.actor(), false, 1);
    assertEquals(21, first.totalCount());
    assertEquals(20, first.entries().size());
    assertEquals(1, second.entries().size());
    assertEquals(8, first.pendingCount());
    assertEquals(8, second.pendingCount());
    assertTrue(first.entries().getFirst().isPending(today));
    assertFalse(second.entries().getFirst().isPending(today));
  }

  private Fixture fixture() {
    String ledger = "book_" + UUID.randomUUID();
    String actor = "rem-" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    return new Fixture(ledger, actor);
  }

  private RentalContractResult create(Fixture f, String name, LocalDate end, Integer days) {
    var home =
        rentals.saveRentalHome(
            new SaveRentalHomeCommand(f.ledger(), f.actor(), null, name, "", ""));
    return rentals.createRentalContract(
        new CreateRentalContractCommand(
            f.ledger(),
            f.actor(),
            home.id(),
            "出租方",
            end.minusMonths(6),
            end,
            250000,
            0,
            RentalPaymentCycle.MONTHLY,
            null,
            "",
            null,
            days));
  }

  private record Fixture(String ledger, String actor) {}
}
