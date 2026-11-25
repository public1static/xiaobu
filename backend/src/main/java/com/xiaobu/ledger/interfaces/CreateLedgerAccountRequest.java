package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateLedgerAccountRequest(@NotBlank @Size(max = 32) String name) {}
