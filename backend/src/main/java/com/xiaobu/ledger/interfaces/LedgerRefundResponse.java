package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

record LedgerRefundResponse(
    String id, long amountCents, LocalDate receivedOn, String accountId, String accountName) {}
