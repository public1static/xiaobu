package com.xiaobu.ledger.application;

import java.util.List;

public record FixedBillPlanPageResult(
    List<FixedBillPlanResult> entries, long totalCount, int page, int pageSize) {}
