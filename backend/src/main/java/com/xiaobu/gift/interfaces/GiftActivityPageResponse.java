package com.xiaobu.gift.interfaces;

import java.util.List;

record GiftActivityPageResponse(
    List<GiftActivityResponse> entries,
    long incomeCents,
    long expenseCents,
    long totalCount,
    int page,
    int pageSize) {}
