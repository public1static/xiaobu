package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerMembership;
import java.util.List;
import java.util.Optional;

public interface LedgerMembershipRepository {

  boolean existsByLedgerAndMember(String ledgerId, String actorId);

  List<LedgerMembership> findByMember(String actorId);

  Optional<LedgerMembership> findByLedgerAndMember(String ledgerId, String actorId);

  void lockLedgerMembershipChanges(String ledgerId);

  void add(LedgerMembership membership);
}
