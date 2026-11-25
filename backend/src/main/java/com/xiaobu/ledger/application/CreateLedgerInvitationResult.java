package com.xiaobu.ledger.application;

import java.time.Instant;

public record CreateLedgerInvitationResult(String token, Instant expiresAt) {}
