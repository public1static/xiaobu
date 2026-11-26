package com.xiaobu.ledger.domain;

import java.time.Instant;

public record LedgerInvitation(
    String tokenDigest,
    String ledgerId,
    String inviterId,
    String inviterName,
    Instant expiresAt,
    String acceptedBy) {

  public boolean isExpiredAt(Instant currentTime) {
    return !expiresAt.isAfter(currentTime);
  }
}
