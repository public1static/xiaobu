package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.Expense;
import java.time.YearMonth;
import java.util.List;

public interface ExpenseRepository {

  void add(String ledgerId, String actorId, Expense expense);

  /**
   * The creator filter is a preview scope, not a substitute for ledger membership authorization.
   */
  List<Expense> findByMonthAndCreator(String ledgerId, String creatorId, YearMonth month);
}
