package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record FixedBillCandidateResult(
    String id,
    long version,
    LocalDate bookedOn,
    long amountCents,
    String note,
    String categoryCode,
    String accountName) {}
