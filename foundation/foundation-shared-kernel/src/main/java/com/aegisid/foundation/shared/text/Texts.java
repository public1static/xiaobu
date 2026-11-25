package com.aegisid.foundation.shared.text;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public final class Texts {

  private Texts() {}

  public static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public static String require(String value, String fieldName) {
    String normalizedFieldName = trimToNull(fieldName);
    if (normalizedFieldName == null) {
      throw new IllegalArgumentException("fieldName must not be blank");
    }
    if (!hasText(value)) {
      throw new IllegalArgumentException(normalizedFieldName + " must not be blank");
    }
    return value.trim();
  }

  public static String require(
      String value, Supplier<? extends RuntimeException> exceptionSupplier) {
    Objects.requireNonNull(exceptionSupplier, "exceptionSupplier must not be null");
    if (!hasText(value)) {
      throw exceptionSupplier.get();
    }
    return value.trim();
  }

  public static String requireRaw(String value, String fieldName) {
    String normalizedFieldName = trimToNull(fieldName);
    if (normalizedFieldName == null) {
      throw new IllegalArgumentException("fieldName must not be blank");
    }
    if (!hasText(value)) {
      throw new IllegalArgumentException(normalizedFieldName + " must not be blank");
    }
    return value;
  }

  public static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String text = value.trim();
    return text.isBlank() ? null : text;
  }

  public static String trimToEmpty(String value) {
    String text = trimToNull(value);
    return text == null ? "" : text;
  }

  public static String toAsciiSlug(String value) {
    return trimToEmpty(value)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
  }
}
