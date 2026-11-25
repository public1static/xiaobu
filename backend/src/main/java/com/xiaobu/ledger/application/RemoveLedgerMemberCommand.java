package com.xiaobu.ledger.application;

public record RemoveLedgerMemberCommand(String ledgerId, String actorId, String memberId) {}
