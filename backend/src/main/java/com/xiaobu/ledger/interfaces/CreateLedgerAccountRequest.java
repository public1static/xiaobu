package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateLedgerAccountRequest(
    @NotBlank @Size(max = 40) String name,
    Long openingBalanceCents,
    String type,
    Boolean balanceKnown) {}
