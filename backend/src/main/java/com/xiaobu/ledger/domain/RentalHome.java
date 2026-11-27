package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;

public record RentalHome(String id, String ledgerId, String name, String address, String note) {
  public RentalHome {
    name = name == null ? "" : name.strip();
    address = address == null ? "" : address.strip();
    note = note == null ? "" : note.strip();
    if (name.isEmpty() || name.length() > 40 || address.length() > 120 || note.length() > 100) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_HOME_INVALID"), "请检查房屋名称、地址和备注");
    }
  }
}
