package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

record RecordExpenseRequest(
    @NotNull Long amountCents, @NotNull LocalDate bookedOn, String categoryCode, String note) {}
