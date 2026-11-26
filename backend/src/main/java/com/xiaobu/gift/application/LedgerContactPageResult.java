package com.xiaobu.gift.application;

import java.util.List;

public record LedgerContactPageResult(
    List<GiftContactResult> entries, long totalCount, int page, int pageSize) {}
