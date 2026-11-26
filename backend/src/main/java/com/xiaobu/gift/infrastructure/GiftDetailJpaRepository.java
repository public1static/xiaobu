package com.xiaobu.gift.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface GiftDetailJpaRepository extends JpaRepository<GiftDetailEntity, String> {
  Optional<GiftDetailEntity> findByLedgerIdAndTransactionId(String ledgerId, String transactionId);

  Optional<GiftDetailEntity> findByLedgerIdAndTransactionIdAndDeletedFalse(
      String ledgerId, String transactionId);
}
