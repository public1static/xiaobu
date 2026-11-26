package com.xiaobu.ledger.application;

import java.util.List;

public record LedgerTransactionDetailResult(
    LedgerTransactionResult transaction,
    String createdBy,
    String accountName,
    long version,
    boolean canModify,
    List<LedgerRefundResult> refunds,
    List<TransactionExtensionResult> extensions) {}
