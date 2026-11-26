package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;

public record CreateLedgerCategoryCommand(
    String ledgerId, String actorId, TransactionDirection direction, String name) {}
