package com.aegisid.foundation.web.page;

import com.aegisid.foundation.shared.page.PageQuery;
import com.aegisid.foundation.shared.page.SortDirection;
import com.aegisid.foundation.shared.page.SortOrder;
import java.util.List;
import java.util.Locale;

/**
 * Flat HTTP query parameters for paged endpoints.
 *
 * @param page 1-based page number
 * @param size page size
 * @param sort repeated sort parameters, for example {@code sort=createdAt,DESC}
 */
public record PageRequestParams(Integer page, Integer size, List<String> sort) {

  public static final int DEFAULT_PAGE = 1;

  public static final int DEFAULT_SIZE = 20;

  public PageQuery toPageQuery() {
    return new PageQuery(
        page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size, sortOrders());
  }

  private List<SortOrder> sortOrders() {
    if (sort == null) {
      return List.of();
    }
    return sort.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(PageRequestParams::sortOrder)
        .toList();
  }

  private static SortOrder sortOrder(String value) {
    String[] parts = value.split(",", 2);
    String field = parts[0].trim();
    SortDirection direction =
        parts.length == 1 || parts[1].isBlank()
            ? SortDirection.ASC
            : SortDirection.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
    return new SortOrder(field, direction);
  }
}
