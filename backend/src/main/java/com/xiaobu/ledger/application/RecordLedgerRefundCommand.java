package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record RecordLedgerRefundCommand(
    String ledgerId,
    String transactionId,
    String actorId,
    long expectedVersion,
    long amountCents,
    LocalDate receivedOn,
    String accountId) {}
