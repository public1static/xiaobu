package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.YearMonth;

// 一个账本同一个自然月只维护一项总预算。
public record LedgerMonthlyBudget(String ledgerId, YearMonth month, long amountCents) {
  public static final long MAX_AMOUNT_CENTS = 9_999_999_999L;

  public LedgerMonthlyBudget {
    if (amountCents < 1 || amountCents > MAX_AMOUNT_CENTS) {
      throw new FoundationException(new ErrorCode("XIAOBU_INVALID_BUDGET_AMOUNT"), "请输入有效的预算金额");
    }
  }
}
