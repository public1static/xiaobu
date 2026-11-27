package com.xiaobu.ledger.application;

import java.util.List;

public record FixedBillCandidatePageResult(
    List<FixedBillCandidateResult> entries, long totalCount, int page, int pageSize) {}
