package com.xiaobu.ledger.domain;

public record LedgerTransaction(String id, String createdBy, LedgerTransactionDetails details) {}
