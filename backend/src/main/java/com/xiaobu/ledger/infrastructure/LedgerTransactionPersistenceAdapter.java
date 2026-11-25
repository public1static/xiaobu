package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerTransactionRepository;
import com.xiaobu.ledger.domain.LedgerTransaction;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class LedgerTransactionPersistenceAdapter implements LedgerTransactionRepository {

  private final LedgerTransactionJpaRepository transactions;
  private final LedgerTransactionPersistenceMapper mapper;

  LedgerTransactionPersistenceAdapter(
      LedgerTransactionJpaRepository transactions, LedgerTransactionPersistenceMapper mapper) {
    this.transactions = transactions;
    this.mapper = mapper;
  }

  @Override
  public void add(String ledgerId, String actorId, LedgerTransaction transaction) {
    transactions.save(mapper.toNewEntity(transaction, ledgerId, actorId));
  }

  @Override
  public List<LedgerTransaction> findByLedgerAndMonth(String ledgerId, YearMonth month) {
    return transactions
        .findActiveByLedgerAndDateRange(ledgerId, month.atDay(1), month.atEndOfMonth())
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
