package com.xiaobu.ledger.interfaces;

import java.util.List;

record LedgerStatementPageResponse(
    List<LedgerTransactionResponse> entries,
    List<LedgerStatementDayResponse> days,
    long incomeCents,
    long expenseCents,
    long balanceCents,
    long totalCount,
    int page,
    int pageSize) {}
