package com.xiaobu.ledger.infrastructure;

import java.util.Optional;
import org.springframework.data.repository.Repository;

interface PersonalLedgerJpaRepository extends Repository<PersonalLedgerEntity, String> {

  <S extends PersonalLedgerEntity> S saveAndFlush(S entity);

  Optional<PersonalLedgerEntity> findByOwnerId(String ownerId);

  boolean existsByIdAndOwnerId(String id, String ownerId);
}
