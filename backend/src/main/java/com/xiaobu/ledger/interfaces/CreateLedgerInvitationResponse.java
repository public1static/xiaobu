package com.xiaobu.ledger.interfaces;

import java.time.Instant;

record CreateLedgerInvitationResponse(String token, Instant expiresAt) {}
