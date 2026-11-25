package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import java.util.Set;

/** Values shared by a new expense and the stored record. Currency is CNY. */
public record ExpenseDetails(
    long amountCents, LocalDate bookedOn, String categoryCode, String note) {

  private static final long MAX_AMOUNT_CENTS = 9_999_999_999L;
  private static final Set<String> CATEGORIES =
      Set.of(
          "DINING",
          "GROCERIES",
          "TRANSIT",
          "DAILY",
          "HOUSING",
          "SHOPPING",
          "ENTERTAINMENT",
          "TRAVEL",
          "HEALTH",
          "EDUCATION",
          "OTHER");

  public ExpenseDetails {
    note = note == null ? "" : note.strip();
    if (amountCents < 1 || amountCents > MAX_AMOUNT_CENTS) {
      throw invalid("金额必须大于 0，且不能超过 99,999,999.99 元");
    }
    if (bookedOn == null || bookedOn.getYear() < 1900 || bookedOn.getYear() > 9999) {
      throw invalid("请选择有效日期");
    }
    if (categoryCode == null || !CATEGORIES.contains(categoryCode)) {
      throw invalid("请选择可用的支出分类");
    }
    if (note.length() > 100) {
      throw invalid("备注最多 100 个字符");
    }
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_EXPENSE_INVALID"), message);
  }
}
