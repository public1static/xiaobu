package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerMembershipRepository;
import com.xiaobu.ledger.domain.LedgerMemberRole;
import com.xiaobu.ledger.domain.LedgerMembership;
import java.time.Instant;
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
  public boolean existsActiveByLedgerAndMember(String ledgerId, String actorId) {
    return memberships.existsByLedgerIdAndMemberIdAndRemovedAtIsNull(ledgerId, actorId);
  }

  @Override
  public List<LedgerMembership> findActiveByMember(String actorId) {
    return memberships.findByMemberIdAndRemovedAtIsNullOrderByIdAsc(actorId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public Optional<LedgerMembership> findByLedgerAndMember(String ledgerId, String actorId) {
    return memberships.findByLedgerIdAndMemberId(ledgerId, actorId).map(mapper::toDomain);
  }

  @Override
  public boolean lockLedgerMembershipChanges(String ledgerId) {
    return ledgers.findByIdForUpdate(ledgerId).isPresent();
  }

  @Override
  public List<LedgerMembership> findActiveByLedger(String ledgerId) {
    return memberships.findByLedgerIdAndRemovedAtIsNullOrderByIdAsc(ledgerId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public void changeRole(String membershipId, LedgerMemberRole role) {
    memberships.findById(membershipId).orElseThrow().changeRole(role);
  }

  @Override
  public void remove(String membershipId, Instant removedAt) {
    memberships.findById(membershipId).orElseThrow().remove(removedAt);
  }

  @Override
  public void restoreReadOnly(String membershipId) {
    memberships.findById(membershipId).orElseThrow().restoreReadOnly();
  }

  @Override
  public void add(LedgerMembership membership) {
    memberships.save(mapper.toNewEntity(membership));
  }
}
