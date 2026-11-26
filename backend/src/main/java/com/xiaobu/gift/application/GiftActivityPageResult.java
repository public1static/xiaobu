package com.xiaobu.gift.application;

import java.util.List;

public record GiftActivityPageResult(
    List<GiftActivityResult> entries,
    long incomeCents,
    long expenseCents,
    long totalCount,
    int page,
    int pageSize) {}
