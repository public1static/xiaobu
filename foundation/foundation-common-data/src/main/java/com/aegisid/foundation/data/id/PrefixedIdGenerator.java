package com.aegisid.foundation.data.id;

import java.util.Objects;

/** Generates UUID-backed IDs with a stable, validated textual prefix. */
public final class PrefixedIdGenerator implements IdGenerator {

  private final IdPrefix prefix;
  private final EntityIdGenerator generator;

  public PrefixedIdGenerator(String prefix) {
    this(new IdPrefix(prefix), new UuidV7EntityIdGenerator());
  }

  public PrefixedIdGenerator(IdPrefix prefix, EntityIdGenerator generator) {
    this.prefix = Objects.requireNonNull(prefix, "prefix must not be null");
    this.generator = Objects.requireNonNull(generator, "generator must not be null");
  }

  @Override
  public String generateId() {
    return generator.next(prefix);
  }
}
