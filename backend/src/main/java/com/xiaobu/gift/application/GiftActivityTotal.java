package com.xiaobu.gift.application;

import com.xiaobu.ledger.domain.TransactionDirection;

public record GiftActivityTotal(
    TransactionDirection direction, long amountCents, long entryCount) {}
