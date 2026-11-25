package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record RecordLedgerAccountTransferCommand(
    String ledgerId,
    String actorId,
    String sourceAccountId,
    String targetAccountId,
    long amountCents,
    LocalDate bookedOn,
    String note) {}
