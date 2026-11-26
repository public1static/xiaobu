package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;

public record TransactionExtensionContext(
    String ledgerId,
    String actorId,
    String transactionId,
    TransactionDirection direction,
    String categoryCode) {}
