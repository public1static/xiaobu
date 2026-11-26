package com.xiaobu.gift.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

public record GiftActivityResult(
    String transactionId,
    String bookId,
    String contactId,
    String personName,
    String distinction,
    String reason,
    String note,
    LocalDate bookedOn,
    long amountCents,
    TransactionDirection direction) {}
