package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.LedgerMonthlyBudget;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record SetLedgerMonthlyBudgetRequest(
    @NotNull @Positive @Max(LedgerMonthlyBudget.MAX_AMOUNT_CENTS) Long amountCents) {}
