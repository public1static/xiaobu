package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerMemberRole;
import com.xiaobu.ledger.domain.LedgerMembership;

public record LedgerMemberResult(String memberId, LedgerMemberRole role) {

  static LedgerMemberResult from(LedgerMembership membership) {
    return new LedgerMemberResult(membership.memberId(), membership.role());
  }
}
