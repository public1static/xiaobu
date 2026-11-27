package com.xiaobu.ledger.infrastructure;

import java.util.Optional;
import org.springframework.data.repository.Repository;

interface LedgerManualReminderJpaRepository extends Repository<LedgerManualReminderEntity, String> {

  Optional<LedgerManualReminderEntity> findByLedgerIdAndCreatedByAndIdAndDeletedFalse(
      String ledgerId, String actorId, String id);

  LedgerManualReminderEntity save(LedgerManualReminderEntity reminder);
}
