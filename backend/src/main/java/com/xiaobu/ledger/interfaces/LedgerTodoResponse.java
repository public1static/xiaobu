package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

record LedgerTodoResponse(
    String source,
    String sourceId,
    String occurrence,
    LocalDate dueOn,
    String status,
    String title,
    String subtitle,
    Long amountCents) {}
