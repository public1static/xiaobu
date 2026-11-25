package com.aegisid.foundation.shared.page;

import java.util.List;
import java.util.Objects;

/**
 * 1-based external page request.
 *
 * @param page external page number, starting from 1
 * @param size page size, from 1 to 200
 * @param sort external sort orders
 */
public record PageQuery(int page, int size, List<SortOrder> sort) {

  public static final int MIN_PAGE = 1;

  public static final int MIN_SIZE = 1;

  public static final int MAX_SIZE = 200;

  public PageQuery(int page, int size) {
    this(page, size, List.of());
  }

  public PageQuery {
    if (page < MIN_PAGE) {
      throw new IllegalArgumentException("Page number must be greater than or equal to 1");
    }
    if (size < MIN_SIZE || size > MAX_SIZE) {
      throw new IllegalArgumentException("Page size must be between 1 and 200");
    }
    sort = List.copyOf(Objects.requireNonNull(sort, "sort must not be null"));
  }

  public int toInternalPageIndex() {
    return page - 1;
  }
}
