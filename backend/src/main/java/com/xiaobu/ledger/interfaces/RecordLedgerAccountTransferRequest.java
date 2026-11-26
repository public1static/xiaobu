package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record RecordLedgerAccountTransferRequest(
    @NotBlank @Size(max = 48) String sourceAccountId,
    @NotBlank @Size(max = 48) String targetAccountId,
    @NotNull Long amountCents,
    @NotNull LocalDate bookedOn,
    @Size(max = 100) String note) {}
