package com.xiaobu.ledger.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface LedgerMembershipJpaRepository extends Repository<LedgerMembershipEntity, String> {

  <S extends LedgerMembershipEntity> S save(S entity);

  boolean existsByLedgerIdAndMemberIdAndRemovedAtIsNull(String ledgerId, String memberId);

  Optional<LedgerMembershipEntity> findByLedgerIdAndMemberId(String ledgerId, String memberId);

  List<LedgerMembershipEntity> findByMemberIdAndRemovedAtIsNullOrderByIdAsc(String memberId);

  List<LedgerMembershipEntity> findByLedgerIdAndRemovedAtIsNullOrderByIdAsc(String ledgerId);

  Optional<LedgerMembershipEntity> findById(String id);
}
