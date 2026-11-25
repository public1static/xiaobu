package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

public record EditLedgerTransactionCommand(
    String ledgerId,
    String actorId,
    String transactionId,
    long expectedVersion,
    TransactionDirection direction,
    long amountCents,
    LocalDate bookedOn,
    String categoryCode,
    String note,
    String accountId) {}
