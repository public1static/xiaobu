package com.xiaobu.ledger.domain;

import java.time.Instant;

public record LedgerMembership(
    String id,
    String ledgerId,
    String memberId,
    String inviterName,
    LedgerMemberRole role,
    Instant removedAt) {

  public boolean isActive() {
    return removedAt == null;
  }

  public boolean canRecord() {
    return isActive() && role == LedgerMemberRole.CAN_RECORD;
  }
}
