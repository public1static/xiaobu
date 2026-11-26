package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;

record ConfigureRentalExpiryReminderRequest(@NotNull Integer remindDays) {}
