package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerInvitationRepository;
import com.xiaobu.ledger.domain.LedgerInvitation;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LedgerInvitationPersistenceAdapter implements LedgerInvitationRepository {

  private final LedgerInvitationJpaRepository invitations;
  private final LedgerInvitationPersistenceMapper mapper;

  LedgerInvitationPersistenceAdapter(
      LedgerInvitationJpaRepository invitations, LedgerInvitationPersistenceMapper mapper) {
    this.invitations = invitations;
    this.mapper = mapper;
  }

  @Override
  public void add(LedgerInvitation invitation) {
    invitations.save(mapper.toNewEntity(invitation));
  }

  @Override
  public Optional<LedgerInvitation> findByTokenDigest(String tokenDigest) {
    return invitations.findById(tokenDigest).map(mapper::toDomain);
  }

  @Override
  public Optional<LedgerInvitation> findByTokenDigestForUpdate(String tokenDigest) {
    return invitations.findByTokenDigestForUpdate(tokenDigest).map(mapper::toDomain);
  }

  @Override
  public void markAccepted(String tokenDigest, String actorId) {
    invitations.findById(tokenDigest).orElseThrow().accept(actorId);
  }
}
