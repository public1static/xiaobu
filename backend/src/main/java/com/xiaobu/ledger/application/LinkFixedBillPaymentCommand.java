package com.xiaobu.ledger.application;

import java.time.YearMonth;

public record LinkFixedBillPaymentCommand(
    String ledgerId,
    String actorId,
    String billId,
    YearMonth month,
    String transactionId,
    long version) {}
