package com.xiaobu.ledger.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LedgerTodoQuery(String ledgerId, String actorId, LocalDateTime now) {
  public LocalDate today() {
    return now.toLocalDate();
  }
}
