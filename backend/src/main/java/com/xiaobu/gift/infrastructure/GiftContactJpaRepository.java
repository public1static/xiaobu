package com.xiaobu.gift.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface GiftContactJpaRepository extends JpaRepository<GiftContactEntity, String> {
  List<GiftContactEntity> findByLedgerIdOrderByNameAscDistinctionAsc(String ledgerId);

  Optional<GiftContactEntity> findByLedgerIdAndId(String ledgerId, String id);
}
