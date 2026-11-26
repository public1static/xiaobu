package com.xiaobu.ledger.application;

public record DeletedLedgerTransactionResult(LedgerTransactionResult transaction, long version) {}
