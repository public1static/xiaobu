package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record StopFixedBillPlanRequest(@NotNull LocalDate expectedStopOn) {}
