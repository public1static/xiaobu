package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.Expense;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseStore {

  void insert(String ledgerId, String actorId, Expense expense);

  List<Expense> list(String ledgerId, String actorId, LocalDate start, LocalDate end);
}
