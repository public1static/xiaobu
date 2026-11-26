package com.xiaobu.ledger.application;

import java.time.YearMonth;

public record RentalPaymentDetail(String billId, YearMonth month, String billName)
    implements TransactionExtensionData {}
