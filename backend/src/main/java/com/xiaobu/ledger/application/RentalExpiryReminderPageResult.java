package com.xiaobu.ledger.application;

import java.time.LocalDate;
import java.util.List;

public record RentalExpiryReminderPageResult(
    List<RentalExpiryReminderResult> entries,
    long totalCount,
    long pendingCount,
    int page,
    int pageSize,
    LocalDate today) {}
