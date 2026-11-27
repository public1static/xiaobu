package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record EditRentalDepositCommand(
    String ledgerId,
    String actorId,
    String contractId,
    String depositId,
    long expectedVersion,
    long amountCents,
    LocalDate bookedOn,
    String accountId,
    String note) {}
