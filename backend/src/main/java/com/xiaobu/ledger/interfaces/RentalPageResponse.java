package com.xiaobu.ledger.interfaces;

import java.util.List;

public record RentalPageResponse<T>(List<T> entries, long totalCount, int page, int pageSize) {}
