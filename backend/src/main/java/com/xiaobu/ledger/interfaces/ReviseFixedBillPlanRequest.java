package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ReviseFixedBillPlanRequest(
    @NotBlank @Size(max = 40) String name,
    @NotNull LocalDate firstDueOn,
    @Positive long amountCents,
    @NotBlank @Size(max = 48) String categoryCode,
    @Size(max = 48) String accountId) {}
