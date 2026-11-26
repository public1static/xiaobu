package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record LedgerTodoResult(
    String source,
    String sourceId,
    String occurrence,
    LocalDate dueOn,
    String status,
    String title,
    String subtitle,
    Long amountCents) {}
