package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

public record FixedBillCandidateResponse(
    String id,
    long version,
    LocalDate bookedOn,
    long amountCents,
    String note,
    String categoryCode,
    String accountName) {}
