package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveRentalHomeRequest(
    @NotBlank @Size(max = 40) String name,
    @Size(max = 120) String address,
    @Size(max = 100) String note) {}
