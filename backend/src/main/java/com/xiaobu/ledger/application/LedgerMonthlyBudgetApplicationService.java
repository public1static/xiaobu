package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerMonthlyBudget;
import java.time.YearMonth;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerMonthlyBudgetApplicationService {

  private final PersonalLedgerApplicationService ledgers;
  private final LedgerMonthlyBudgetRepository budgets;
  private final LedgerStatementQueryRepository statements;

  LedgerMonthlyBudgetApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerMonthlyBudgetRepository budgets,
      LedgerStatementQueryRepository statements) {
    this.ledgers = ledgers;
    this.budgets = budgets;
    this.statements = statements;
  }

  @Transactional(readOnly = true)
  public LedgerMonthlyBudgetResult getLedgerMonthlyBudget(
      String ledgerId, String actorId, YearMonth month) {
    ledgers.requireReadAccess(ledgerId, actorId);
    var query = new ListLedgerTransactionsQuery(ledgerId, actorId, month);
    Long amount =
        budgets
            .findActiveBudget(ledgerId, month)
            .map(LedgerMonthlyBudget::amountCents)
            .orElse(null);
    return summarize(query, month, amount);
  }

  @Transactional
  public LedgerMonthlyBudgetResult setLedgerMonthlyBudget(SetLedgerMonthlyBudgetCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var query =
        new ListLedgerTransactionsQuery(command.ledgerId(), command.actorId(), command.month());
    var budget =
        new LedgerMonthlyBudget(command.ledgerId(), command.month(), command.amountCents());
    budgets.setBudget(budget, command.actorId());
    return summarize(query, command.month(), budget.amountCents());
  }

  // 同流水月汇总的净支出口径，不读任何明细页。
  private LedgerMonthlyBudgetResult summarize(
      ListLedgerTransactionsQuery query, YearMonth month, Long amount) {
    long used =
        statements.summarizeByDay(query).stream()
            .mapToLong(LedgerStatementDayResult::expenseCents)
            .sum();
    return new LedgerMonthlyBudgetResult(
        month.toString(), amount, used, amount == null ? null : amount - used);
  }
}
