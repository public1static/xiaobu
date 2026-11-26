package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.xiaobu.ledger.application.LedgerManualReminderRepository;
import com.xiaobu.ledger.domain.LedgerManualReminder;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LedgerManualReminderPersistenceAdapter implements LedgerManualReminderRepository {

  private static final IdPrefix REMINDER_PREFIX = new IdPrefix("reminder");
  private final LedgerManualReminderJpaRepository rows;
  private final EntityIdGenerator ids;
  private final LedgerManualReminderOccurrenceJpaRepository occurrences;

  LedgerManualReminderPersistenceAdapter(
      LedgerManualReminderJpaRepository rows,
      EntityIdGenerator ids,
      LedgerManualReminderOccurrenceJpaRepository occurrences) {
    this.rows = rows;
    this.ids = ids;
    this.occurrences = occurrences;
  }

  @Override
  public Optional<LedgerManualReminder> findOwnedReminder(
      String ledgerId, String actorId, String id) {
    return rows.findByLedgerIdAndCreatedByAndIdAndDeletedFalse(ledgerId, actorId, id)
        .map(LedgerManualReminderEntity::toDomain);
  }

  @Override
  public LedgerManualReminder createReminder(
      String ledgerId, String actorId, LedgerManualReminder reminder) {
    return rows.save(
            new LedgerManualReminderEntity(ids.next(REMINDER_PREFIX), ledgerId, actorId, reminder))
        .toDomain();
  }

  @Override
  public LedgerManualReminder updateReminder(
      String ledgerId, String actorId, LedgerManualReminder reminder) {
    var row =
        rows.findByLedgerIdAndCreatedByAndIdAndDeletedFalse(ledgerId, actorId, reminder.id())
            .orElseThrow();
    row.revise(reminder, actorId);
    return row.toDomain();
  }

  @Override
  public Optional<LedgerManualReminder> findCompletedOccurrence(String reminderId, long revision) {
    return occurrences
        .findByReminderIdAndRevisionAndDeletedFalse(reminderId, revision)
        .map(LedgerManualReminderOccurrenceEntity::toDomain);
  }

  @Override
  public void recordCompletedOccurrence(LedgerManualReminder reminder, String actorId) {
    occurrences.save(new LedgerManualReminderOccurrenceEntity(reminder, actorId));
  }

  @Override
  public void revertCompletedOccurrence(String reminderId, long revision, String actorId) {
    occurrences
        .findByReminderIdAndRevisionAndDeletedFalse(reminderId, revision)
        .orElseThrow()
        .revert(actorId);
  }
}
