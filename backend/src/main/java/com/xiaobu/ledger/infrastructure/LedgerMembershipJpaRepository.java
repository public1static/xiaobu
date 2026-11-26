package com.xiaobu.ledger.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface LedgerMembershipJpaRepository extends Repository<LedgerMembershipEntity, String> {

  <S extends LedgerMembershipEntity> S save(S entity);

  boolean existsByLedgerIdAndMemberId(String ledgerId, String memberId);

  Optional<LedgerMembershipEntity> findByLedgerIdAndMemberId(String ledgerId, String memberId);

  List<LedgerMembershipEntity> findByMemberIdOrderByIdAsc(String memberId);
}
