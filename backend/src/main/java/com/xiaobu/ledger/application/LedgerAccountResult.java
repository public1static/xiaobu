package com.xiaobu.ledger.application;

public record LedgerAccountResult(
    String id,
    String name,
    long openingBalanceCents,
    long recordedBalanceCents,
    long balanceCents) {}
