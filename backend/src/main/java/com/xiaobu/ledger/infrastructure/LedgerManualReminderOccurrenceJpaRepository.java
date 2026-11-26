package com.xiaobu.ledger.infrastructure;

import java.util.Optional;
import org.springframework.data.repository.Repository;

interface LedgerManualReminderOccurrenceJpaRepository
    extends Repository<LedgerManualReminderOccurrenceEntity, String> {

  Optional<LedgerManualReminderOccurrenceEntity> findByReminderIdAndRevisionAndDeletedFalse(
      String reminderId, long revision);

  LedgerManualReminderOccurrenceEntity save(LedgerManualReminderOccurrenceEntity occurrence);
}
