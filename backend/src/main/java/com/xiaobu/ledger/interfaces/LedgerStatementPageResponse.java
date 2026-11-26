package com.xiaobu.ledger.interfaces;

import java.util.List;

public record LedgerStatementPageResponse(
    List<LedgerTransactionResponse> entries,
    List<LedgerStatementDayResponse> days,
    long incomeCents,
    long expenseCents,
    long balanceCents,
    long totalCount,
    int page,
    int pageSize,
    List<LedgerStatementGroupResponse> groups) {}
