package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreateLedgerCategoryRequest(
    @NotBlank @Pattern(regexp = "EXPENSE|INCOME") String direction,
    @NotBlank @Size(max = 24) String name) {}
