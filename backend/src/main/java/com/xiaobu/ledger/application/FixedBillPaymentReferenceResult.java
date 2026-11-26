package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record FixedBillPaymentReferenceResult(
    String paymentId, String billName, LocalDate periodStart) {}
