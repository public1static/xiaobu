package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

record ChangeLedgerMemberRoleRequest(
    @NotNull @Pattern(regexp = "READ_ONLY|CAN_RECORD") String role) {}
