package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RespondToLedgerInvitationRequest(@NotBlank @Size(max = 43) String token) {}
