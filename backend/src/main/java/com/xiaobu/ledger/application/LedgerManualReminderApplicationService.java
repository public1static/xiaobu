package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerManualReminder;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerManualReminderApplicationService {

  private static final Set<Integer> LEAD_DAYS = Set.of(0, 1, 3, 7);
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerManualReminderRepository reminders;

  LedgerManualReminderApplicationService(
      PersonalLedgerApplicationService ledgers, LedgerManualReminderRepository reminders) {
    this.ledgers = ledgers;
    this.reminders = reminders;
  }

  @Transactional(readOnly = true)
  public LedgerManualReminder getLedgerManualReminder(
      String ledgerId, String actorId, String reminderId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return findOwnedReminder(ledgerId, actorId, reminderId);
  }

  @Transactional
  public LedgerManualReminder saveLedgerManualReminder(SaveLedgerManualReminderCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    String title = command.title().strip();
    if (title.isEmpty()
        || title.length() > 60
        || command.dueOn().getYear() < 1900
        || command.dueOn().getYear() > 2100
        || command.time().getSecond() != 0
        || command.time().getNano() != 0
        || !LEAD_DAYS.contains(command.leadDays())) {
      throw new FoundationException(new ErrorCode("XIAOBU_INVALID_REMINDER"), "请检查提醒内容、日期和时间");
    }
    var reminder =
        LedgerManualReminder.schedule(
            command.reminderId(), title, command.dueOn(), command.time(), command.leadDays());
    if (command.reminderId() == null) {
      return reminders.createReminder(command.ledgerId(), command.actorId(), reminder);
    }
    if (findOwnedReminder(command.ledgerId(), command.actorId(), command.reminderId())
        .completed()) {
      throw new FoundationException(new ErrorCode("XIAOBU_REMINDER_COMPLETED"), "请先撤销办结，再编辑提醒");
    }
    return reminders.updateReminder(command.ledgerId(), command.actorId(), reminder);
  }

  @Transactional
  public LedgerManualReminder setLedgerManualReminderCompletion(
      String ledgerId, String actorId, String reminderId, boolean completed) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    var current = findOwnedReminder(ledgerId, actorId, reminderId);
    if (current.completed() == completed) return current;
    var next =
        new LedgerManualReminder(
            current.id(), current.title(), current.dueOn(), current.remindAt(), completed);
    return reminders.updateReminder(ledgerId, actorId, next);
  }

  private LedgerManualReminder findOwnedReminder(String ledgerId, String actorId, String id) {
    return reminders
        .findOwnedReminder(ledgerId, actorId, id)
        .orElseThrow(
            () -> new FoundationException(new ErrorCode("XIAOBU_REMINDER_NOT_FOUND"), "找不到这条提醒"));
  }
}
