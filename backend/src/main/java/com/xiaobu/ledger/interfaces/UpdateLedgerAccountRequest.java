package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record UpdateLedgerAccountRequest(
    @NotBlank @Size(max = 40) String name,
    @NotBlank String type,
    @NotNull Boolean active,
    boolean preserveBalance,
    @Min(-9999999999L) @Max(9999999999L) Long currentBalanceCents) {}
