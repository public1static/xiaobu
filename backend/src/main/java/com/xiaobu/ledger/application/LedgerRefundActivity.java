package com.xiaobu.ledger.application;

import java.time.LocalDate;

/** Refund projection using the original expense's current category. */
public record LedgerRefundActivity(
    String id,
    String transactionId,
    long amountCents,
    LocalDate receivedOn,
    String categoryCode,
    String note,
    String accountId) {}
