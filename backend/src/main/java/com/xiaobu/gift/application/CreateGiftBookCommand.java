package com.xiaobu.gift.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

public record CreateGiftBookCommand(
    String ledgerId,
    String actorId,
    String name,
    TransactionDirection direction,
    LocalDate defaultDate,
    String defaultReason) {}
