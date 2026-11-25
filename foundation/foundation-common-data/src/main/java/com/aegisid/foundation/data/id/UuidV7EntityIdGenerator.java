package com.aegisid.foundation.data.id;

import com.github.f4b6a3.uuid.UuidCreator;

/** Generates RFC 9562 UUIDv7-backed entity identifiers. */
public final class UuidV7EntityIdGenerator implements EntityIdGenerator {

  @Override
  public String next(IdPrefix prefix) {
    return prefix.value() + "_" + UuidCreator.getTimeOrderedEpoch();
  }
}
