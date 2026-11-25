package com.xiaobu.ledger.application;

import java.time.YearMonth;

public record ListLedgerTransactionsQuery(String ledgerId, String actorId, YearMonth month) {}
