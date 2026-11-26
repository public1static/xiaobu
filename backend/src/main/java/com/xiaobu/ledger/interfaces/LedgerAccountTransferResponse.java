package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

record LedgerAccountTransferResponse(
    String id,
    String sourceAccountId,
    String targetAccountId,
    long amountCents,
    LocalDate bookedOn,
    String note) {}
