package com.xiaobu.ledger.application;

import java.util.List;

public record FixedBillPageResult(
    List<FixedBillPeriodResult> entries, long totalCount, int page, int pageSize) {}
