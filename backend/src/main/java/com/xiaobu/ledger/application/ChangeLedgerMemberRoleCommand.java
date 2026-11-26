package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerMemberRole;

public record ChangeLedgerMemberRoleCommand(
    String ledgerId, String actorId, String memberId, LedgerMemberRole role) {}
