package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;
import java.util.List;

record RentalExpiryReminderPageResponse(
    List<RentalExpiryReminderResponse> entries,
    long totalCount,
    long pendingCount,
    int page,
    int pageSize,
    LocalDate today) {}
