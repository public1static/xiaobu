package com.xiaobu.ledger.application;

public record ChangeLedgerCategoryStatusCommand(
    String ledgerId, String actorId, String code, boolean active) {}
