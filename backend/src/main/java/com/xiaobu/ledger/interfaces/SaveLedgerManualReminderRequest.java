package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.ManualReminderFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

record SaveLedgerManualReminderRequest(
    @NotBlank @Size(max = 60) String title,
    @NotNull LocalDate dueOn,
    @NotNull LocalTime time,
    @NotNull Integer leadDays,
    @NotNull ManualReminderFrequency frequency,
    @PositiveOrZero Long revision) {}
