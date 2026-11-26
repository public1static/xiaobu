package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record RecordLedgerRefundRequest(
    @NotNull @Min(0) Long version,
    @NotNull @Positive Long amountCents,
    @NotNull LocalDate receivedOn,
    @Size(max = 48) String accountId) {}
