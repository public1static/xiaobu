package com.xiaobu.ledger.application;

import java.time.YearMonth;

public record ListExpensesQuery(String ledgerId, String creatorId, YearMonth month) {}
