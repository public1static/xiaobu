package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

record ExpenseResponse(
    String id, long amountCents, LocalDate bookedOn, String categoryCode, String note) {}
