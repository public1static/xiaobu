package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.TransactionExtensionResult;
import java.util.List;

record LedgerTransactionDetailResponse(
    LedgerTransactionResponse transaction,
    String authorName,
    String accountName,
    long version,
    boolean canModify,
    List<LedgerRefundResponse> refunds,
    List<TransactionExtensionResult> extensions) {}
