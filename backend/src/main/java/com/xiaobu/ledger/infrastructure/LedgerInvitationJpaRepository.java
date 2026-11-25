package com.xiaobu.ledger.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface LedgerInvitationJpaRepository extends Repository<LedgerInvitationEntity, String> {

  <S extends LedgerInvitationEntity> S save(S entity);

  Optional<LedgerInvitationEntity> findById(String tokenDigest);

  // Hibernate generates FOR UPDATE OF with its MySQL 8 dialect; 5.7 requires plain FOR UPDATE.
  @Query(
      value = "SELECT * FROM ledger_invitations WHERE token_digest = :tokenDigest FOR UPDATE",
      nativeQuery = true)
  Optional<LedgerInvitationEntity> findByTokenDigestForUpdate(
      @Param("tokenDigest") String tokenDigest);
}
