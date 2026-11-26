package com.xiaobu.ledger.application;

public record ChangeLedgerTransactionStateCommand(
    String ledgerId, String actorId, String transactionId, long expectedVersion) {}
