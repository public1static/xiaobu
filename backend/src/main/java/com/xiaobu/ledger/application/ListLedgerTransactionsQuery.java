package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public record ListLedgerTransactionsQuery(
    String ledgerId, String actorId, LocalDate from, LocalDate through, String keyword) {

  public ListLedgerTransactionsQuery(String ledgerId, String actorId, YearMonth month) {
    this(ledgerId, actorId, month.atDay(1), month.atEndOfMonth(), "");
  }

  public ListLedgerTransactionsQuery {
    if (from == null
        || through == null
        || from.getYear() < 1900
        || through.getYear() > 9999
        || from.isAfter(through)
        || ChronoUnit.DAYS.between(from, through) > 365) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_INVALID_DATE_RANGE"), "请选择有效日期，范围最多 366 天");
    }
    keyword = keyword == null ? "" : keyword.strip();
    if (keyword.length() > 80) {
      throw new FoundationException(new ErrorCode("XIAOBU_SEARCH_TOO_LONG"), "搜索词最多 80 个字符");
    }
  }
}
