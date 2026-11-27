package com.xiaobu.ledger.application;

import java.time.YearMonth;

public record ListFixedBillCandidatesQuery(
    String ledgerId, String actorId, String billId, YearMonth month, String keyword, int page) {}
