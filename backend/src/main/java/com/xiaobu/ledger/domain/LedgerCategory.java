package com.xiaobu.ledger.domain;

public record LedgerCategory(
    String id,
    String ledgerId,
    String code,
    TransactionDirection direction,
    String name,
    int displayOrder,
    boolean active) {}
