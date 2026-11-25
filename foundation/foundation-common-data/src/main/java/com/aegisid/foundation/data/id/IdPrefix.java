package com.aegisid.foundation.data.id;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable public type prefix for an entity identifier. */
public record IdPrefix(String value) {

  private static final Pattern VALUE_PATTERN = Pattern.compile("[a-z][a-z0-9]{1,11}");

  public IdPrefix {
    value = Objects.requireNonNull(value, "value must not be null").trim().toLowerCase(Locale.ROOT);
    if (!VALUE_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "ID prefix must contain 2 to 12 lowercase letters or digits");
    }
  }
}
