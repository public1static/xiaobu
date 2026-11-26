package com.xiaobu.ledger.application;

public record CreateLedgerAccountCommand(
    String ledgerId,
    String actorId,
    String name,
    long openingBalanceCents,
    String type,
    boolean balanceKnown) {
  // 兼容只提供期初余额的创建调用。
  public CreateLedgerAccountCommand(
      String ledgerId, String actorId, String name, long openingBalanceCents) {
    this(ledgerId, actorId, name, openingBalanceCents, null, true);
  }

  public CreateLedgerAccountCommand(String ledgerId, String actorId, String name) {
    this(ledgerId, actorId, name, 0);
  }
}
