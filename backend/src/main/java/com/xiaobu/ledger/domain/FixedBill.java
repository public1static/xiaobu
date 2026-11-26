package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import java.time.YearMonth;

public record FixedBill(
    String id,
    String ledgerId,
    String name,
    LocalDate firstDueOn,
    long amountCents,
    String categoryCode,
    String accountId) {
  public FixedBill {
    name = name == null ? "" : name.strip();
    if (name.isEmpty()
        || name.length() > 40
        || firstDueOn == null
        || firstDueOn.getYear() < 1900
        || firstDueOn.getYear() > 9999
        || amountCents < 1
        || amountCents > 9_999_999_999L) {
      throw new FoundationException(new ErrorCode("XIAOBU_FIXED_BILL_INVALID"), "请检查账单名称、金额和首次到期日");
    }
  }

  public LocalDate dueOn(YearMonth month) {
    return month.atDay(Math.min(firstDueOn.getDayOfMonth(), month.lengthOfMonth()));
  }
}
