package com.aegisid.foundation.data.id;

/** Generates stable textual identifiers for persistence boundaries. */
@FunctionalInterface
public interface IdGenerator {

  /**
   * Creates a new identifier.
   *
   * @return generated identifier value
   */
  String generateId();
}
