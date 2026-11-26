package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record RestoreLedgerTransactionRequest(@NotNull @Min(0) Long version) {}
