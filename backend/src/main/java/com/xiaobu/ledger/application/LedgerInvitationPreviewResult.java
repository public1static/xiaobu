package com.xiaobu.ledger.application;

import java.time.Instant;

public record LedgerInvitationPreviewResult(
    String inviterName, Instant expiresAt, boolean accepted) {}
