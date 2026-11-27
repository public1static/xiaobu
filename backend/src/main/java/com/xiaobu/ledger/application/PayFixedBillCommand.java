package com.xiaobu.ledger.application;

import java.time.LocalDate;
import java.time.YearMonth;

public record PayFixedBillCommand(
    String ledgerId,
    String actorId,
    String billId,
    YearMonth month,
    LocalDate paidOn,
    long amountCents,
    String accountId) {}
