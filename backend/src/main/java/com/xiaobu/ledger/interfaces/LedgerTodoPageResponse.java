package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;
import java.util.List;

record LedgerTodoPageResponse(
    List<LedgerTodoResponse> entries,
    long totalCount,
    long pendingCount,
    int page,
    int pageSize,
    LocalDate today) {}
