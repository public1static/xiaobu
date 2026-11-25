package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerMemberRole;
import com.xiaobu.ledger.domain.LedgerMembership;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LedgerMembershipRepository {

  boolean existsActiveByLedgerAndMember(String ledgerId, String actorId);

  List<LedgerMembership> findActiveByMember(String actorId);

  Optional<LedgerMembership> findByLedgerAndMember(String ledgerId, String actorId);

  List<LedgerMembership> findActiveByLedger(String ledgerId);

  boolean lockLedgerMembershipChanges(String ledgerId);

  void changeRole(String membershipId, LedgerMemberRole role);

  void remove(String membershipId, Instant removedAt);

  void restoreReadOnly(String membershipId);

  void add(LedgerMembership membership);
}
