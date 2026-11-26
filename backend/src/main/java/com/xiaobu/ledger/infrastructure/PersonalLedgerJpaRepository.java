package com.xiaobu.ledger.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface PersonalLedgerJpaRepository extends Repository<PersonalLedgerEntity, String> {

  <S extends PersonalLedgerEntity> S saveAndFlush(S entity);

  Optional<PersonalLedgerEntity> findByOwnerId(String ownerId);

  // Hibernate generates FOR UPDATE OF with its MySQL 8 dialect; 5.7 requires plain FOR UPDATE.
  @Query(
      value = "SELECT id, owner_id FROM personal_ledgers WHERE id = :id FOR UPDATE",
      nativeQuery = true)
  Optional<PersonalLedgerEntity> findByIdForUpdate(@Param("id") String id);

  boolean existsByIdAndOwnerId(String id, String ownerId);
}
