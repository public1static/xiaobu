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
  private final PersonalLedgerApplicationService ledgers;

  public ExpenseApplicationService(
      ExpenseRepository repository,
      EntityIdGenerator idGenerator,
      PersonalLedgerApplicationService ledgers) {
    this.repository = repository;
    this.idGenerator = idGenerator;
    this.ledgers = ledgers;
  }

  @Transactional
  public ExpenseResult recordExpense(RecordExpenseCommand command) {
    ledgers.requireOwnership(command.ledgerId(), command.actorId());
    var details =
        new ExpenseDetails(
            command.amountCents(), command.bookedOn(), command.categoryCode(), command.note());
    var expense = new Expense(idGenerator.next(EXPENSE_PREFIX), details);
    repository.add(command.ledgerId(), command.actorId(), expense);
    return ExpenseResult.from(expense);
  }

  @Transactional(readOnly = true)
  public List<ExpenseResult> listExpenses(ListExpensesQuery query) {
    ledgers.requireOwnership(query.ledgerId(), query.actorId());
    return repository.findByLedgerAndMonth(query.ledgerId(), query.month()).stream()
        .map(ExpenseResult::from)
        .toList();
  }
}
