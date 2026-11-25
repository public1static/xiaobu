package com.aegisid.foundation.shared.page;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable page result using 1-based external page number.
 *
 * @param items page items
 * @param page external page number, starting from 1
 * @param size page size
 * @param totalElements total matching element count
 * @param totalPages derived total page count
 * @param sort external sort orders
 * @param <T> item type
 */
public record PageResult<T>(
    List<T> items, int page, int size, long totalElements, int totalPages, List<SortOrder> sort) {

  public PageResult(List<T> items, int page, int size, long totalElements) {
    this(items, page, size, totalElements, calculateTotalPages(totalElements, size), List.of());
  }

  public PageResult(List<T> items, int page, int size, long totalElements, int totalPages) {
    this(items, page, size, totalElements, totalPages, List.of());
  }

  public PageResult {
    items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    sort = List.copyOf(Objects.requireNonNull(sort, "sort must not be null"));
    if (page < PageQuery.MIN_PAGE) {
      throw new IllegalArgumentException("Page number must be greater than or equal to 1");
    }
    if (size < PageQuery.MIN_SIZE || size > PageQuery.MAX_SIZE) {
      throw new IllegalArgumentException("Page size must be between 1 and 200");
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("Total elements must not be negative");
    }
    if (totalPages < 0) {
      throw new IllegalArgumentException("Total pages must not be negative");
    }
    int expectedTotalPages = calculateTotalPages(totalElements, size);
    if (totalPages != expectedTotalPages) {
      throw new IllegalArgumentException("Total pages must match total elements and page size");
    }
  }

  public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new PageResult<>(
        items.stream().map(item -> (R) mapper.apply(item)).toList(),
        page,
        size,
        totalElements,
        totalPages,
        sort);
  }

  private static int calculateTotalPages(long totalElements, int size) {
    if (size < PageQuery.MIN_SIZE || size > PageQuery.MAX_SIZE) {
      throw new IllegalArgumentException("Page size must be between 1 and 200");
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("Total elements must not be negative");
    }
    return Math.toIntExact((totalElements + size - 1) / size);
  }
}
