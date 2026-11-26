package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.LedgerMemberRole;

record LedgerMemberResponse(String memberId, String displayName, LedgerMemberRole role) {}
