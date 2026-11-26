package com.xiaobu.ledger.interfaces;

record LedgerAccountHistoryResponse(
    String id,
    String kind,
    String bookedOn,
    long amountCents,
    String categoryCode,
    String transactionId,
    String sourceAccountId,
    String targetAccountId,
    String note,
    int ownRecord) {}
