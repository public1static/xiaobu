package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

record ChangeLedgerCategoryOrderRequest(
    @NotBlank @Pattern(regexp = "EXPENSE|INCOME") String direction,
    @NotEmpty @Size(max = 64) List<@NotBlank @Size(max = 20) String> codes) {}
