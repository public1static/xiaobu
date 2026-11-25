package com.xiaobu.ledger.interfaces;

import java.util.List;

record LedgerTransactionDetailResponse(
    LedgerTransactionResponse transaction,
    String authorName,
    String accountName,
    long version,
    boolean canModify,
    List<LedgerRefundResponse> refunds) {}
