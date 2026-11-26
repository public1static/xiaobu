package com.xiaobu.ledger.interfaces;

record LedgerTransactionDetailResponse(
    LedgerTransactionResponse transaction,
    String authorName,
    String accountName,
    long version,
    boolean canModify) {}
