package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

public record TransactionExtensionContext(
    String ledgerId,
    String actorId,
    String transactionId,
    TransactionDirection direction,
    String categoryCode,
    LocalDate bookedOn,
    long amountCents,
    String originalCategoryCode) {}
