package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record EditRentalDepositRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @Positive long amountCents,
    @NotNull LocalDate bookedOn,
    @Size(max = 48) String accountId,
    @Size(max = 100) String note) {}
