package com.xiaobu.ledger.application;

public record LedgerTransactionDetailResult(
    LedgerTransactionResult transaction, String createdBy, String accountName) {}
