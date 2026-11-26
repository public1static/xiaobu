package com.xiaobu.ledger.interfaces;

import java.util.List;

public record FixedBillPageResponse(
    List<FixedBillPeriodResponse> entries, long totalCount, int page, int pageSize) {}
