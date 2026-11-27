package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerManualReminder;
import com.xiaobu.ledger.domain.ManualReminderFrequency;
import java.util.Map;
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
  public LedgerManualReminderResult getLedgerManualReminder(
      String ledgerId, String actorId, String reminderId, Long revision) {
    ledgers.requireReadAccess(ledgerId, actorId);
    var current = findOwnedReminder(ledgerId, actorId, reminderId);
    if (revision == null || revision == current.revision()) {
      return new LedgerManualReminderResult(current, false);
    }
    var occurrence =
        reminders.findCompletedOccurrence(reminderId, revision).orElseThrow(this::notFound);
    return new LedgerManualReminderResult(occurrence, canUndo(current, revision));
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
    var replacement =
        LedgerManualReminder.schedule(
            command.reminderId(),
            title,
            command.dueOn(),
            command.time(),
            command.leadDays(),
            command.frequency());
    if (command.reminderId() == null) {
      return reminders.createReminder(command.ledgerId(), command.actorId(), replacement);
    }
    var current = findOwnedReminder(command.ledgerId(), command.actorId(), command.reminderId());
    if (command.revision() == null || command.revision() != current.revision() || !current.open()) {
      throw changed();
    }
    return reminders.updateReminder(
        command.ledgerId(), command.actorId(), current.revise(replacement));
  }

  @Transactional
  public LedgerManualReminder setLedgerManualReminderCompletion(
      String ledgerId, String actorId, String reminderId, long revision, boolean completed) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    var current = findOwnedReminder(ledgerId, actorId, reminderId);
    if (completed) {
      if (!current.open() || current.revision() != revision) {
        throw changed();
      }
      reminders.recordCompletedOccurrence(current, actorId);
      return reminders.updateReminder(ledgerId, actorId, current.advance());
    }
    if (!canUndo(current, revision)) throw changed();
    var occurrence =
        reminders.findCompletedOccurrence(reminderId, revision).orElseThrow(this::changed);
    reminders.revertCompletedOccurrence(reminderId, revision, actorId);
    return reminders.updateReminder(ledgerId, actorId, current.resumeOccurrence(occurrence));
  }

  @Transactional
  public LedgerManualReminder stopLedgerManualReminder(
      String ledgerId, String actorId, String reminderId, long revision) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    var current = findOwnedReminder(ledgerId, actorId, reminderId);
    if (!current.open()
        || current.revision() != revision
        || current.frequency() == ManualReminderFrequency.ONCE) {
      throw changed();
    }
    return reminders.updateReminder(ledgerId, actorId, current.stop());
  }

  private boolean canUndo(LedgerManualReminder current, long revision) {
    return !current.stopped() && current.revision() - 1 == revision;
  }

  private LedgerManualReminder findOwnedReminder(String ledgerId, String actorId, String id) {
    return reminders.findOwnedReminder(ledgerId, actorId, id).orElseThrow(this::notFound);
  }

  private FoundationException notFound() {
    return new FoundationException(
        new ErrorCode("XIAOBU_REMINDER_NOT_FOUND"), ErrorCategory.NOT_FOUND, "找不到这条提醒", Map.of());
  }

  private FoundationException changed() {
    return new FoundationException(
        new ErrorCode("XIAOBU_REMINDER_CHANGED"), ErrorCategory.CONFLICT, "提醒已更新，请刷新后再试", Map.of());
  }
}
