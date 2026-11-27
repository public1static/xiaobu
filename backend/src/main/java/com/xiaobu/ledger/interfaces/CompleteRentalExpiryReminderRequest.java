package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

record CompleteRentalExpiryReminderRequest(@NotNull LocalDate expectedEndsOn) {}
