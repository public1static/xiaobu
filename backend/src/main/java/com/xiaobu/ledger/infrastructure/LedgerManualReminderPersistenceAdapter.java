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

  LedgerManualReminderPersistenceAdapter(
      LedgerManualReminderJpaRepository rows, EntityIdGenerator ids) {
    this.rows = rows;
    this.ids = ids;
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
}
