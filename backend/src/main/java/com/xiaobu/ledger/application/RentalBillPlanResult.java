package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record RentalBillPlanResult(
    String billId, String name, LocalDate firstDueOn, long amountCents) {}
