package com.xiaobu.ledger.interfaces;

import java.util.List;

public record FixedBillCandidatePageResponse(
    List<FixedBillCandidateResponse> entries, long totalCount, int page, int pageSize) {}
