package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.xiaobu.ledger.application.LedgerMonthlyBudgetRepository;
import com.xiaobu.ledger.domain.LedgerMonthlyBudget;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LedgerMonthlyBudgetPersistenceAdapter implements LedgerMonthlyBudgetRepository {

  private static final IdPrefix BUDGET_PREFIX = new IdPrefix("budget");

  private final LedgerMonthlyBudgetJpaRepository rows;
  private final EntityIdGenerator ids;

  LedgerMonthlyBudgetPersistenceAdapter(
      LedgerMonthlyBudgetJpaRepository rows, EntityIdGenerator ids) {
    this.rows = rows;
    this.ids = ids;
  }

  @Override
  public Optional<LedgerMonthlyBudget> findActiveBudget(String ledgerId, YearMonth month) {
    return rows.findByLedgerIdAndMonthStartAndDeletedFalse(ledgerId, month.atDay(1))
        .map(LedgerMonthlyBudgetEntity::toDomain);
  }

  @Override
  public void setBudget(LedgerMonthlyBudget budget, String actorId) {
    rows.findByLedgerIdAndMonthStart(budget.ledgerId(), budget.month().atDay(1))
        .ifPresentOrElse(
            row -> row.revise(budget.amountCents(), actorId),
            () ->
                rows.save(new LedgerMonthlyBudgetEntity(ids.next(BUDGET_PREFIX), budget, actorId)));
  }
}
