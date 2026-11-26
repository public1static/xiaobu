package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.RentalPaymentCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateRentalContractRequest(
    @NotBlank @Size(max = 48) String homeId,
    @NotBlank @Size(max = 40) String landlordName,
    @NotNull LocalDate startsOn,
    @NotNull LocalDate endsOn,
    @Positive long monthlyRentCents,
    @PositiveOrZero long depositCents,
    @NotNull RentalPaymentCycle cycle,
    LocalDate firstDueOn,
    @Size(max = 100) String note,
    @Size(max = 48) String existingBillId) {}
