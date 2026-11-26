package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.TransactionDirection;

record LedgerCategoryResponse(
    String code, TransactionDirection direction, String name, boolean active) {}
