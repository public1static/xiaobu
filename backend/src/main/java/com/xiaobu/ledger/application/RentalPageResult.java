package com.xiaobu.ledger.application;

import java.util.List;

public record RentalPageResult<T>(List<T> entries, long totalCount, int page, int pageSize) {}
