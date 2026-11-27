package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkRentalBillRequest(@NotBlank @Size(max = 48) String billId) {}
