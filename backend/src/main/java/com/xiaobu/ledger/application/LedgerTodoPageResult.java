package com.xiaobu.ledger.application;

import java.time.LocalDate;
import java.util.List;

public record LedgerTodoPageResult(
    List<LedgerTodoResult> entries,
    long totalCount,
    long pendingCount,
    int page,
    int pageSize,
    LocalDate today) {}
