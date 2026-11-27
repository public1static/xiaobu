package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerManualReminder;
import java.util.Optional;

public interface LedgerManualReminderRepository {

  Optional<LedgerManualReminder> findOwnedReminder(String ledgerId, String actorId, String id);

  LedgerManualReminder createReminder(
      String ledgerId, String actorId, LedgerManualReminder reminder);

  LedgerManualReminder updateReminder(
      String ledgerId, String actorId, LedgerManualReminder reminder);
}
