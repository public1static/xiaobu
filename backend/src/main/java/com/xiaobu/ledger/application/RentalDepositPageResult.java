package com.xiaobu.ledger.application;

import java.util.List;

public record RentalDepositPageResult(
    List<RentalDepositResult> entries, long heldCents, long totalCount, int page, int pageSize) {}
