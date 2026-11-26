package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerMonthlyBudget;
import java.time.YearMonth;
import java.util.Optional;

public interface LedgerMonthlyBudgetRepository {

  Optional<LedgerMonthlyBudget> findActiveBudget(String ledgerId, YearMonth month);

  void setBudget(LedgerMonthlyBudget budget, String actorId);
}
