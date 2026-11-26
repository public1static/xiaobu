package com.xiaobu.ledger.application;

import java.time.YearMonth;

public record RentalPaymentDraft(String billId, YearMonth month)
    implements TransactionExtensionData {}
