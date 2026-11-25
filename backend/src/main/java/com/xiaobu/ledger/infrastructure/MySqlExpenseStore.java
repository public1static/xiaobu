package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.ExpenseStore;
import com.xiaobu.ledger.domain.Expense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MySqlExpenseStore implements ExpenseStore {

  private final ExpenseJpaRepository rows;

  public MySqlExpenseStore(ExpenseJpaRepository rows) {
    this.rows = rows;
  }

  @Override
  public void insert(String ledgerId, String actorId, Expense expense) {
    var details = expense.details();
    rows.insert(
        expense.id(),
        ledgerId,
        actorId,
        details.amountCents(),
        details.bookedOn(),
        details.categoryCode(),
        details.note());
  }

  @Override
  public List<Expense> list(String ledgerId, String actorId, LocalDate start, LocalDate end) {
    return rows.listByPeriod(ledgerId, actorId, start, end).stream()
        .map(ExpenseEntity::toDomain)
        .toList();
  }
}
