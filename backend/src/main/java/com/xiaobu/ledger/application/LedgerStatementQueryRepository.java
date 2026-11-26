package com.xiaobu.ledger.application;

import java.util.List;

public interface LedgerStatementQueryRepository {
  List<LedgerTransactionResult> findPage(ListLedgerTransactionsQuery query);

  // 汇总所有匹配行，保持与来源明细相同的筛选条件。
  List<LedgerStatementGroupResult> summarizeByGroup(ListLedgerTransactionsQuery query);

  List<LedgerStatementDayResult> summarizeByDay(ListLedgerTransactionsQuery query);
}
