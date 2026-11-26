package com.xiaobu.ledger.application;

public record CreateLedgerInvitationCommand(String ledgerId, String actorId, String displayName) {}
