package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

public record RecordLedgerTransactionCommand(
    String ledgerId,
    String actorId,
    TransactionDirection direction,
    long amountCents,
    LocalDate bookedOn,
    String categoryCode,
    String note,
    String accountId) {}
