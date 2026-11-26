package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.PersonalLedgerRepository;
import com.xiaobu.ledger.domain.PersonalLedger;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PersonalLedgerPersistenceAdapter implements PersonalLedgerRepository {

  private final PersonalLedgerJpaRepository ledgers;
  private final PersonalLedgerPersistenceMapper mapper;

  PersonalLedgerPersistenceAdapter(
      PersonalLedgerJpaRepository ledgers, PersonalLedgerPersistenceMapper mapper) {
    this.ledgers = ledgers;
    this.mapper = mapper;
  }

  @Override
  public Optional<PersonalLedger> findByOwnerId(String ownerId) {
    return ledgers.findByOwnerId(ownerId).map(mapper::toDomain);
  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PersonalLedger addIfAbsent(PersonalLedger ledger) {
    try {
      return mapper.toDomain(ledgers.saveAndFlush(mapper.toNewEntity(ledger)));
    } catch (DataIntegrityViolationException ex) {
      // The insert transaction has rolled back. Another login may have created the same ledger.
      return findByOwnerId(ledger.ownerId()).orElseThrow(() -> ex);
    }
  }

  @Override
  public boolean existsByIdAndOwnerId(String ledgerId, String ownerId) {
    return ledgers.existsByIdAndOwnerId(ledgerId, ownerId);
  }
}
