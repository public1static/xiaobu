package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

public record RentalBillPlanResponse(
    String billId, String name, LocalDate firstDueOn, long amountCents) {}
