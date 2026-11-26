package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.Expense;
import java.time.YearMonth;
import java.util.List;

public interface ExpenseRepository {

  void add(String ledgerId, String actorId, Expense expense);

  List<Expense> findByLedgerAndMonth(String ledgerId, YearMonth month);
}
