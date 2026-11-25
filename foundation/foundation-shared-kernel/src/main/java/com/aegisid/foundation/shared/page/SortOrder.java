package com.aegisid.foundation.shared.page;

import java.util.Objects;

/**
 * External sort order descriptor.
 *
 * @param field external sort field name
 * @param direction sort direction
 */
public record SortOrder(String field, SortDirection direction) {

  public SortOrder {
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("Sort field must not be blank");
    }
    direction = Objects.requireNonNull(direction, "direction must not be null");
  }

  public static SortOrder asc(String field) {
    return new SortOrder(field, SortDirection.ASC);
  }

  public static SortOrder desc(String field) {
    return new SortOrder(field, SortDirection.DESC);
  }
}
