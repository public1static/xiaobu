package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;

public record RentalDeposit(
    String id,
    String ledgerId,
    String contractId,
    RentalDepositKind kind,
    long amountCents,
    LocalDate bookedOn,
    String accountId,
    String note,
    String createdBy,
    long version) {
  public RentalDeposit {
    note = note == null ? "" : note.strip();
    if (kind == null
        || amountCents < 1
        || amountCents > 9_999_999_999L
        || bookedOn == null
        || bookedOn.getYear() < 1900
        || note.length() > 100) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_DEPOSIT_INVALID"), "请检查押金金额和日期");
    }
  }
}
