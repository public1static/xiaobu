package com.xiaobu.gift.interfaces;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

record GiftBookResponse(
    String id,
    String name,
    TransactionDirection direction,
    LocalDate defaultDate,
    String defaultReason,
    boolean archived) {}
