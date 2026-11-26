package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

record LedgerTransactionResponse(
    String id,
    TransactionDirection direction,
    long amountCents,
    LocalDate bookedOn,
    String categoryCode,
    String note,
    String accountId) {}
