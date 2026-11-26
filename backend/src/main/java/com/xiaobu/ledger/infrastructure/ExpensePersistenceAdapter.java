package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.ExpenseRepository;
import com.xiaobu.ledger.domain.Expense;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class ExpensePersistenceAdapter implements ExpenseRepository {

  private final LedgerTransactionJpaRepository transactions;
  private final ExpensePersistenceMapper mapper;

  ExpensePersistenceAdapter(
      LedgerTransactionJpaRepository transactions, ExpensePersistenceMapper mapper) {
    this.transactions = transactions;
    this.mapper = mapper;
  }

  @Override
  public void add(String ledgerId, String actorId, Expense expense) {
    transactions.save(mapper.toNewEntity(expense, ledgerId, actorId));
  }

  @Override
  public List<Expense> findByLedgerAndMonth(String ledgerId, YearMonth month) {
    return transactions
        .findActiveByLedgerAndDateRange(
            ledgerId, TransactionDirection.EXPENSE, month.atDay(1), month.atEndOfMonth())
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
