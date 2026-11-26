package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.LedgerMemberRole;

record JoinedLedgerResponse(String ledgerId, String inviterName, LedgerMemberRole role) {}
