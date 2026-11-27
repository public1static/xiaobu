package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record GenerateRentalBillRequest(@NotNull LocalDate firstDueOn) {}
