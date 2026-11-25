package com.aegisid.foundation.data.id;

/** Generates opaque, globally unique identifiers for persisted business entities. */
@FunctionalInterface
public interface EntityIdGenerator {

  String next(IdPrefix prefix);
}
