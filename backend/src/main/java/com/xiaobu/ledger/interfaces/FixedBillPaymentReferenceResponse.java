package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

public record FixedBillPaymentReferenceResponse(
    String paymentId, String billName, LocalDate periodStart) {}
