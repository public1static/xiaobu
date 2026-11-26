package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerRefundRepository;
import com.xiaobu.ledger.domain.LedgerRefund;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class LedgerRefundPersistenceAdapter implements LedgerRefundRepository {

  private final LedgerRefundJpaRepository refunds;
  private final LedgerRefundPersistenceMapper mapper;

  LedgerRefundPersistenceAdapter(
      LedgerRefundJpaRepository refunds, LedgerRefundPersistenceMapper mapper) {
    this.refunds = refunds;
    this.mapper = mapper;
  }

  @Override
  public void add(LedgerRefund refund) {
    refunds.save(mapper.toNewEntity(refund));
  }

  @Override
  public List<LedgerRefund> findByTransaction(String ledgerId, String transactionId) {
    return refunds
        .findByLedgerIdAndTransactionIdOrderByReceivedOnDescIdDesc(ledgerId, transactionId)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
