package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record RecordLedgerTransactionRequest(
    @NotNull @Pattern(regexp = "EXPENSE|INCOME") String direction,
    @NotNull Long amountCents,
    @NotNull LocalDate bookedOn,
    String categoryCode,
    String note,
    @Size(max = 48) String accountId) {}
