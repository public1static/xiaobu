package com.xiaobu.ledger.interfaces;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

record RecordLedgerTransactionRequest(
    @NotNull @Pattern(regexp = "EXPENSE|INCOME") String direction,
    @NotNull Long amountCents,
    @NotNull LocalDate bookedOn,
    String categoryCode,
    String note,
    @Size(max = 48) String accountId,
    @Size(max = 16) List<@NotNull @Valid TransactionExtensionRequest> extensions) {}
