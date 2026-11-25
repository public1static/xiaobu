package com.aegisid.foundation.shared.id;

/**
 * Immutable typed identifier value object.
 *
 * @param value stable textual identifier value
 * @param <T> marker type for the aggregate or resource identified by this ID
 */
public record TypedId<T>(String value) {

  public TypedId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("TypedId value must not be blank");
    }
    value = value.trim();
  }
}
