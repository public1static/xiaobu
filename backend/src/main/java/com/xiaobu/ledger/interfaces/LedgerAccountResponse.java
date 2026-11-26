package com.xiaobu.ledger.interfaces;

record LedgerAccountResponse(
    String id,
    String name,
    long openingBalanceCents,
    long recordedBalanceCents,
    long balanceCents,
    String type,
    boolean active,
    boolean balanceKnown) {}
