package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;

public record LedgerAccount(String id, String ledgerId, String name, long openingBalanceCents) {

  public static final long MAX_BALANCE_CENTS = 9_999_999_999L;

  public LedgerAccount {
    if (openingBalanceCents < -MAX_BALANCE_CENTS || openingBalanceCents > MAX_BALANCE_CENTS) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_ACCOUNT_OPENING_BALANCE_INVALID"), "期初余额超出金额范围");
    }
    name = name == null ? "" : name.strip();
    if (name.isBlank() || name.length() > 32) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_ACCOUNT_NAME_INVALID"), "账户名称须为 1 至 32 个字符");
    }
  }
}
