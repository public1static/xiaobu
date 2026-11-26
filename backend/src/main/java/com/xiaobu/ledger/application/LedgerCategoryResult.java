package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerCategory;
import com.xiaobu.ledger.domain.TransactionDirection;

public record LedgerCategoryResult(
    String code, TransactionDirection direction, String name, boolean active) {

  static LedgerCategoryResult from(LedgerCategory category) {
    return new LedgerCategoryResult(
        category.code(), category.direction(), category.name(), category.active());
  }
}
