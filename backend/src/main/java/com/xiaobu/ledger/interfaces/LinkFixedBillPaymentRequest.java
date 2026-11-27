package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LinkFixedBillPaymentRequest(
    @NotBlank @Size(max = 48) String transactionId, @NotNull @PositiveOrZero Long version) {}
