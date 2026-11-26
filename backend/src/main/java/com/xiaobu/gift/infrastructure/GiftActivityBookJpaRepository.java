package com.xiaobu.gift.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface GiftActivityBookJpaRepository extends JpaRepository<GiftActivityBookEntity, String> {
  List<GiftActivityBookEntity> findByLedgerIdOrderByCreatedAtDesc(String ledgerId);

  Optional<GiftActivityBookEntity> findByLedgerIdAndId(String ledgerId, String id);
}
