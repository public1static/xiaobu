package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.RentalDepositKind;
import java.time.LocalDate;

record RentalDepositResponse(
    String id,
    RentalDepositKind kind,
    long amountCents,
    LocalDate bookedOn,
    String accountId,
    String accountName,
    String note,
    long version,
    boolean canModify) {}
