package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalDepositKind;
import java.time.LocalDate;

public record RecordRentalDepositCommand(
    String ledgerId,
    String actorId,
    String contractId,
    RentalDepositKind kind,
    long amountCents,
    LocalDate bookedOn,
    String accountId,
    String note) {}
