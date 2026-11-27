package com.xiaobu.gift.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface GiftContactJpaRepository extends JpaRepository<GiftContactEntity, String> {
  List<GiftContactEntity> findByLedgerIdAndGiftMemberTrueOrderByNameAscDistinctionAsc(
      String ledgerId);

  List<GiftContactEntity> findByLedgerIdAndNameIgnoreCase(String ledgerId, String name);

  Page<GiftContactEntity> findByLedgerIdAndNameIgnoreCaseOrderByDistinctionAscIdAsc(
      String ledgerId, String name, Pageable pageable);

  Optional<GiftContactEntity> findByLedgerIdAndId(String ledgerId, String id);
}
