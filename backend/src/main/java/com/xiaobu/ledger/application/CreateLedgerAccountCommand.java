package com.xiaobu.ledger.application;

public record CreateLedgerAccountCommand(String ledgerId, String actorId, String name) {}
