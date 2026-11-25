package com.aegisid.foundation.web.api;

import com.aegisid.foundation.shared.page.PageResult;
import com.aegisid.foundation.shared.page.SortOrder;
import java.util.List;
import java.util.Objects;

/**
 * API page payload using 1-based page numbers.
 *
 * @param items page items
 * @param page external page number, starting at 1
 * @param size page size
 * @param totalElements total matching element count
 * @param totalPages total page count
 * @param sort external sort order
 * @param <T> item type
 */
public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages, List<Sort> sort) {

  /**
   * External sort descriptor.
   *
   * @param field sort field name
   * @param direction sort direction, for example ASC or DESC
   */
  public record Sort(String field, String direction) {}

  public PageResponse(List<T> items, int page, int size, long totalElements, int totalPages) {
    this(items, page, size, totalElements, totalPages, List.of());
  }

  public PageResponse {
    items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    if (page < 1) {
      throw new IllegalArgumentException("Page number must be greater than or equal to 1");
    }
    sort = sort == null ? List.of() : List.copyOf(sort);
  }

  public static <T> PageResponse<T> from(PageResult<T> result) {
    Objects.requireNonNull(result, "result must not be null");
    return new PageResponse<>(
        result.items(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages(),
        result.sort().stream().map(PageResponse::from).toList());
  }

  private static Sort from(SortOrder sortOrder) {
    return new Sort(sortOrder.field(), sortOrder.direction().name());
  }
}
