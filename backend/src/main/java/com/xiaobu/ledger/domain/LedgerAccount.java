package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;

public record LedgerAccount(String id, String ledgerId, String name) {

  public LedgerAccount {
    name = name == null ? "" : name.strip();
    if (name.isBlank() || name.length() > 32) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_ACCOUNT_NAME_INVALID"), "账户名称须为 1 至 32 个字符");
    }
  }
}
