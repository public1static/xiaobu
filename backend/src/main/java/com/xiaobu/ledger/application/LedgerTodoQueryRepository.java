package com.xiaobu.ledger.application;

public interface LedgerTodoQueryRepository {

  LedgerTodoPageResult listLedgerTodos(LedgerTodoQuery query, boolean completed, int page);

  long countPendingLedgerTodos(LedgerTodoQuery query);
}
