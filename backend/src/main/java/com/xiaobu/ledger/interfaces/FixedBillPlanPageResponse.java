package com.xiaobu.ledger.interfaces;

import java.util.List;

public record FixedBillPlanPageResponse(
    List<FixedBillPlanResponse> entries, long totalCount, int page, int pageSize) {}
