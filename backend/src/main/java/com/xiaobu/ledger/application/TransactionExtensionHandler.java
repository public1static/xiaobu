package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.Optional;

public interface TransactionExtensionHandler<T extends TransactionExtensionData> {
  String type();

  Class<T> draftType();

  Optional<TransactionExtensionCapability> capability(
      TransactionDirection direction, String categoryCode);

  Optional<? extends TransactionExtensionData> read(TransactionExtensionContext context);

  /** Mutation must detect even an existing attachment whose display is not permitted. */
  default Optional<? extends TransactionExtensionData> readForMutation(
      TransactionExtensionContext context) {
    return read(context);
  }

  default boolean allowsModification(TransactionExtensionContext context) {
    return true;
  }

  default void validateRetained(
      TransactionExtensionContext context, TransactionExtensionData detail) {}

  void upsert(TransactionExtensionContext context, T detail);

  void remove(TransactionExtensionContext context);

  default void write(TransactionExtensionContext context, TransactionExtensionData detail) {
    upsert(context, draftType().cast(detail));
  }
}
