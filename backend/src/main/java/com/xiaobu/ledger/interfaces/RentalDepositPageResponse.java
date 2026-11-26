package com.xiaobu.ledger.interfaces;

import java.util.List;

record RentalDepositPageResponse(
    List<RentalDepositResponse> entries, long heldCents, long totalCount, int page, int pageSize) {}
