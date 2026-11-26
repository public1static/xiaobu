package com.xiaobu.ledger.interfaces;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record EditLedgerTransactionRequest(
    @NotNull @Min(0) Long version, @NotNull @Valid RecordLedgerTransactionRequest draft) {}
