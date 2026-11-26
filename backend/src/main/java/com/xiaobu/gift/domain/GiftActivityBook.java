package com.xiaobu.gift.domain;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

public record GiftActivityBook(
    String id,
    String name,
    TransactionDirection direction,
    LocalDate defaultDate,
    String defaultReason,
    boolean archived) {}
