package com.xiaobu.ledger.domain;

import java.time.LocalDate;

public record LedgerRefund(
    String id,
    String ledgerId,
    String transactionId,
    long amountCents,
    LocalDate receivedOn,
    String accountId,
    String actorId) {}
