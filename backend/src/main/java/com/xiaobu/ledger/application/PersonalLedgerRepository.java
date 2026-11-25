package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.PersonalLedger;
import java.util.Optional;

public interface PersonalLedgerRepository {

  Optional<PersonalLedger> findByOwnerId(String ownerId);

  PersonalLedger addIfAbsent(PersonalLedger ledger);

  boolean existsByIdAndOwnerId(String ledgerId, String ownerId);
}
