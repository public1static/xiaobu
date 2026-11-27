package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.RentalDepositKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record RecordRentalDepositRequest(
    @NotNull RentalDepositKind kind,
    @Positive long amountCents,
    @NotNull LocalDate bookedOn,
    @Size(max = 48) String accountId,
    @Size(max = 100) String note) {}
