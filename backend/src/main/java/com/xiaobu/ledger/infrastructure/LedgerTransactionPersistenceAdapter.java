package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerTransactionRepository;
import com.xiaobu.ledger.domain.LedgerTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
  public List<LedgerTransaction> findByLedgerAndDateRange(
      String ledgerId, LocalDate from, LocalDate through, String keyword) {
    return transactions.findActiveByLedgerAndDateRange(ledgerId, from, through, keyword).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public Optional<LedgerTransaction> findActiveByLedgerAndId(
      String ledgerId, String transactionId) {
    return transactions
        .findByLedgerIdAndIdAndDeletedFalse(ledgerId, transactionId)
        .map(mapper::toDomain);
  }
}
