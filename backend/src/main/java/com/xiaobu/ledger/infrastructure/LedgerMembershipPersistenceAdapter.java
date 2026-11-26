package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerMembershipRepository;
import com.xiaobu.ledger.domain.LedgerMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LedgerMembershipPersistenceAdapter implements LedgerMembershipRepository {

  private final LedgerMembershipJpaRepository memberships;
  private final PersonalLedgerJpaRepository ledgers;
  private final LedgerMembershipPersistenceMapper mapper;

  LedgerMembershipPersistenceAdapter(
      LedgerMembershipJpaRepository memberships,
      PersonalLedgerJpaRepository ledgers,
      LedgerMembershipPersistenceMapper mapper) {
    this.memberships = memberships;
    this.ledgers = ledgers;
    this.mapper = mapper;
  }

  @Override
  public boolean existsByLedgerAndMember(String ledgerId, String actorId) {
    return memberships.existsByLedgerIdAndMemberId(ledgerId, actorId);
  }

  @Override
  public List<LedgerMembership> findByMember(String actorId) {
    return memberships.findByMemberIdOrderByIdAsc(actorId).stream().map(mapper::toDomain).toList();
  }

  @Override
  public Optional<LedgerMembership> findByLedgerAndMember(String ledgerId, String actorId) {
    return memberships.findByLedgerIdAndMemberId(ledgerId, actorId).map(mapper::toDomain);
  }

  @Override
  public void lockLedgerMembershipChanges(String ledgerId) {
    ledgers.findByIdForUpdate(ledgerId).orElseThrow();
  }

  @Override
  public void add(LedgerMembership membership) {
    memberships.save(mapper.toNewEntity(membership));
  }
}
