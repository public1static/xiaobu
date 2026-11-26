package com.xiaobu.ledger.application;

import java.util.List;

public record LedgerStatementPageResult(
    List<LedgerTransactionResult> entries,
    List<LedgerStatementDayResult> days,
    long incomeCents,
    long expenseCents,
    long balanceCents,
    long totalCount,
    int page,
    int pageSize) {}
