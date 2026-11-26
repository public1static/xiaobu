package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;

public record LedgerAccountTransfer(
    String id,
    String ledgerId,
    String sourceAccountId,
    String targetAccountId,
    long amountCents,
    LocalDate bookedOn,
    String note,
    String createdBy) {

  public LedgerAccountTransfer {
    if (sourceAccountId == null
        || targetAccountId == null
        || sourceAccountId.equals(targetAccountId)) {
      throw invalid("请选择两个不同的账户");
    }
    if (amountCents < 1 || amountCents > LedgerAccount.MAX_BALANCE_CENTS) {
      throw invalid("转账金额必须在 0.01 至 99,999,999.99 元之间");
    }
    if (bookedOn == null || bookedOn.getYear() < 1900 || bookedOn.getYear() > 9999) {
      throw invalid("请选择有效日期");
    }
    note = note == null ? "" : note.strip();
    if (note.length() > 100) {
      throw invalid("备注最多 100 个字符");
    }
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_ACCOUNT_TRANSFER_INVALID"), message);
  }
}
