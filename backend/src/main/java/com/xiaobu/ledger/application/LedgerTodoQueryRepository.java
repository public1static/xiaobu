package com.xiaobu.ledger.application;

import java.time.LocalDate;

public interface LedgerTodoQueryRepository {

  LedgerTodoPageResult listLedgerTodos(
      String ledgerId, LocalDate today, boolean completed, int page);

  long countPendingLedgerTodos(String ledgerId, LocalDate today);
}
