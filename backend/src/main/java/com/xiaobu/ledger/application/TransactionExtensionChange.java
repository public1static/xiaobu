package com.xiaobu.ledger.application;

public record TransactionExtensionChange(
    String type, Operation operation, TransactionExtensionData detail) {
  public enum Operation {
    UPSERT,
    REMOVE
  }
}
