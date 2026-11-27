package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PayFixedBillRequest(
    @NotNull LocalDate paidOn, @Positive long amountCents, @Size(max = 48) String accountId) {}
