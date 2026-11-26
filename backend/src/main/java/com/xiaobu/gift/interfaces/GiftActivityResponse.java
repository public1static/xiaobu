package com.xiaobu.gift.interfaces;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

record GiftActivityResponse(
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
