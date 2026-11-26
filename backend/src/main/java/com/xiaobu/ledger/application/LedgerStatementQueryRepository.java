package com.xiaobu.ledger.application;

import java.util.List;

public interface LedgerStatementQueryRepository {
  List<LedgerTransactionResult> findPage(ListLedgerTransactionsQuery query);

  List<LedgerStatementDayResult> summarizeByDay(ListLedgerTransactionsQuery query);
}
