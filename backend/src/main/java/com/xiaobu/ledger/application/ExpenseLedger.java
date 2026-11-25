package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.xiaobu.ledger.domain.Expense;
import com.xiaobu.ledger.domain.ExpenseDetails;
import java.time.YearMonth;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense")
public class ExpenseLedger {

  private static final IdPrefix EXPENSE_PREFIX = new IdPrefix("exp");

  private final ExpenseStore store;
  private final EntityIdGenerator ids;

  public ExpenseLedger(ExpenseStore store, EntityIdGenerator ids) {
    this.store = store;
    this.ids = ids;
  }

  @Transactional
  public Expense record(String ledgerId, String actorId, ExpenseDetails details) {
    var expense = new Expense(ids.next(EXPENSE_PREFIX), details);
    store.insert(ledgerId, actorId, expense);
    return expense;
  }

  @Transactional(readOnly = true)
  public List<Expense> list(String ledgerId, String actorId, YearMonth month) {
    return store.list(ledgerId, actorId, month.atDay(1), month.atEndOfMonth());
  }
}
