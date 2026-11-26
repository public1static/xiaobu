package com.xiaobu.ledger.domain;

import java.time.LocalDate;

public record FixedBillPayment(
    String id,
    String billId,
    LocalDate period,
    String transactionId,
    LocalDate paidOn,
    long amountCents) {}
