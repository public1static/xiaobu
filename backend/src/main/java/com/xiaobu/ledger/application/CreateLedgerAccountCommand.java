package com.xiaobu.ledger.application;

public record CreateLedgerAccountCommand(
    String ledgerId, String actorId, String name, long openingBalanceCents) {

  public CreateLedgerAccountCommand(String ledgerId, String actorId, String name) {
    this(ledgerId, actorId, name, 0);
  }
}
