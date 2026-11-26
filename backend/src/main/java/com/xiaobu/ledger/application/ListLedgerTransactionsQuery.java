package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public record ListLedgerTransactionsQuery(
    String ledgerId,
    String actorId,
    LocalDate from,
    LocalDate through,
    String keyword,
    int page,
    int pageSize) {

  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_PAGE_SIZE = 100;

  public ListLedgerTransactionsQuery(
      String ledgerId, String actorId, LocalDate from, LocalDate through, String keyword) {
    this(ledgerId, actorId, from, through, keyword, 0, DEFAULT_PAGE_SIZE);
  }

  public ListLedgerTransactionsQuery(String ledgerId, String actorId, YearMonth month) {
    this(ledgerId, actorId, month.atDay(1), month.atEndOfMonth(), "");
  }

  public ListLedgerTransactionsQuery {
    if (page < 0
        || pageSize < 1
        || pageSize > MAX_PAGE_SIZE
        || (long) page * pageSize > Integer.MAX_VALUE) {
      throw new FoundationException(new ErrorCode("XIAOBU_INVALID_PAGE"), "请选择有效的分页参数");
    }
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
