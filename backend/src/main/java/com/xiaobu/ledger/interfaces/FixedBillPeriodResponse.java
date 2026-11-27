package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

public record FixedBillPeriodResponse(
    String billId,
    String name,
    LocalDate dueOn,
    long amountCents,
    String categoryCode,
    String accountId,
    String transactionId,
    LocalDate paidOn,
    Long paidAmountCents) {}
