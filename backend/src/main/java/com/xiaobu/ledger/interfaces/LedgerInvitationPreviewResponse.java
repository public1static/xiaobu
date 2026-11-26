package com.xiaobu.ledger.interfaces;

import java.time.Instant;

record LedgerInvitationPreviewResponse(String inviterName, Instant expiresAt, boolean accepted) {}
