package com.xiaobu.gift.infrastructure;

import com.xiaobu.gift.application.GiftDetailRepository;
import com.xiaobu.gift.domain.GiftDetail;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class GiftDetailPersistenceAdapter implements GiftDetailRepository {
  private final GiftDetailJpaRepository details;

  GiftDetailPersistenceAdapter(GiftDetailJpaRepository details) {
    this.details = details;
  }

  @Override
  public Optional<GiftDetail> findActive(String ledgerId, String transactionId) {
    return details
        .findByLedgerIdAndTransactionIdAndDeletedFalse(ledgerId, transactionId)
        .map(GiftDetailEntity::toDomain);
  }

  @Override
  public void save(String ledgerId, String transactionId, String actorId, GiftDetail detail) {
    details
        .findByLedgerIdAndTransactionId(ledgerId, transactionId)
        .ifPresentOrElse(
            existing -> existing.revise(actorId, detail),
            () -> details.save(new GiftDetailEntity(ledgerId, transactionId, actorId, detail)));
  }

  @Override
  public void remove(String ledgerId, String transactionId, String actorId) {
    details
        .findByLedgerIdAndTransactionIdAndDeletedFalse(ledgerId, transactionId)
        .orElseThrow()
        .remove(actorId);
  }
}
