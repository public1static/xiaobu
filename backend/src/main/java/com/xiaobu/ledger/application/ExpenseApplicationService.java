package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.xiaobu.ledger.domain.Expense;
import com.xiaobu.ledger.domain.ExpenseDetails;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense")
public class ExpenseApplicationService {

  private static final IdPrefix EXPENSE_PREFIX = new IdPrefix("exp");

  private final ExpenseRepository repository;
  private final EntityIdGenerator idGenerator;

  public ExpenseApplicationService(ExpenseRepository repository, EntityIdGenerator idGenerator) {
    this.repository = repository;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public ExpenseResult recordExpense(RecordExpenseCommand command) {
    var details =
        new ExpenseDetails(
            command.amountCents(), command.bookedOn(), command.categoryCode(), command.note());
    var expense = new Expense(idGenerator.next(EXPENSE_PREFIX), details);
    repository.add(command.ledgerId(), command.actorId(), expense);
    return ExpenseResult.from(expense);
  }

  @Transactional(readOnly = true)
  public List<ExpenseResult> listExpenses(ListExpensesQuery query) {
    return repository
        .findByMonthAndCreator(query.ledgerId(), query.creatorId(), query.month())
        .stream()
        .map(ExpenseResult::from)
        .toList();
  }
}
