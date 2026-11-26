package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;

/** Values shared by a new transaction and the stored record. Currency is CNY. */
public record LedgerTransactionDetails(
    TransactionDirection direction,
    long amountCents,
    LocalDate bookedOn,
    String categoryCode,
    String note,
    String accountId) {

  private static final long MAX_AMOUNT_CENTS = 9_999_999_999L;

  public LedgerTransactionDetails {
    if (direction == null) {
      throw invalid("请选择收入或支出");
    }
    note = note == null ? "" : note.strip();
    if (amountCents < 1 || amountCents > MAX_AMOUNT_CENTS) {
      throw invalid("金额必须大于 0，且不能超过 99,999,999.99 元");
    }
    if (bookedOn == null || bookedOn.getYear() < 1900 || bookedOn.getYear() > 9999) {
      throw invalid("请选择有效日期");
    }
    if (!LedgerCategoryDefaults.matchesDefault(categoryCode, direction)
        && !LedgerCategoryDefaults.isCustomCode(categoryCode)) {
      throw invalid("分类与收支方向不匹配");
    }
    if (note.length() > 100) {
      throw invalid("备注最多 100 个字符");
    }
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_TRANSACTION_INVALID"), message);
  }
}
