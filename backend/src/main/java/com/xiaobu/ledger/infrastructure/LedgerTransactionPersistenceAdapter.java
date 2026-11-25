package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerTransactionRepository;
import com.xiaobu.ledger.domain.LedgerTransaction;
import com.xiaobu.ledger.domain.LedgerTransactionDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

  @Override
  public Optional<LedgerTransaction> findByLedgerAndId(String ledgerId, String transactionId) {
    return transactions.findByLedgerIdAndId(ledgerId, transactionId).map(mapper::toDomain);
  }

  @Override
  public List<LedgerTransaction> findRecentlyDeleted(String ledgerId, String creatorFilter) {
    return transactions.findRecentlyDeleted(ledgerId, creatorFilter, Pageable.ofSize(50)).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public void edit(
      String ledgerId, String transactionId, String actorId, LedgerTransactionDetails details) {
    transactions
        .findByLedgerIdAndId(ledgerId, transactionId)
        .orElseThrow()
        .revise(details, actorId);
  }

  @Override
  public void changeDeletion(
      String ledgerId, String transactionId, String actorId, boolean deleted) {
    transactions
        .findByLedgerIdAndId(ledgerId, transactionId)
        .orElseThrow()
        .changeDeletion(deleted, actorId);
  }
}
