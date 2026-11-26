package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerMemberRole;
import com.xiaobu.ledger.domain.LedgerMembership;

public record JoinedLedgerResult(String ledgerId, String inviterName, LedgerMemberRole role) {

  static JoinedLedgerResult from(LedgerMembership membership) {
    return new JoinedLedgerResult(
        membership.ledgerId(), membership.inviterName(), membership.role());
  }
}
