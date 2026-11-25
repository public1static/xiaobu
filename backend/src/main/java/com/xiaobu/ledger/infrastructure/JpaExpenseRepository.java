package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.ExpenseRepository;
import com.xiaobu.ledger.domain.Expense;
import jakarta.persistence.EntityManager;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class JpaExpenseRepository implements ExpenseRepository {

  private final EntityManager entityManager;
  private final LedgerTransactionJpaRepository transactions;
  private final ExpensePersistenceMapper mapper;

  JpaExpenseRepository(
      EntityManager entityManager,
      LedgerTransactionJpaRepository transactions,
      ExpensePersistenceMapper mapper) {
    this.entityManager = entityManager;
    this.transactions = transactions;
    this.mapper = mapper;
  }

  @Override
  public void add(String ledgerId, String actorId, Expense expense) {
    entityManager.persist(mapper.toNewEntity(expense, ledgerId, actorId));
  }

  @Override
  public List<Expense> findByMonthAndCreator(String ledgerId, String creatorId, YearMonth month) {
    return transactions
        .findActiveByCreatorAndDateRange(
            ledgerId, creatorId, TransactionDirection.EXPENSE, month.atDay(1), month.atEndOfMonth())
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
