package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.PersonalLedger;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-expense")
public class PersonalLedgerApplicationService {

  private static final IdPrefix LEDGER_PREFIX = new IdPrefix("book");
  private static final ErrorCode LEDGER_ACCESS_DENIED =
      new ErrorCode("XIAOBU_LEDGER_ACCESS_DENIED");

  private final PersonalLedgerRepository ledgers;
  private final EntityIdGenerator ids;

  PersonalLedgerApplicationService(PersonalLedgerRepository ledgers, EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.ids = ids;
  }

  public PersonalLedger openPersonalLedger(String ownerId) {
    return ledgers
        .findByOwnerId(ownerId)
        .orElseGet(() -> ledgers.addIfAbsent(new PersonalLedger(ids.next(LEDGER_PREFIX), ownerId)));
  }

  public void requireOwnership(String ledgerId, String actorId) {
    if (!ledgers.existsByIdAndOwnerId(ledgerId, actorId)) {
      throw new FoundationException(
          LEDGER_ACCESS_DENIED, ErrorCategory.FORBIDDEN, "无权访问这个账本", Map.of());
    }
  }
}
