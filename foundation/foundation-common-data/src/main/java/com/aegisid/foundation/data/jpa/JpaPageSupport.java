package com.aegisid.foundation.data.jpa;

import com.aegisid.foundation.shared.page.PageQuery;
import com.aegisid.foundation.shared.page.PageResult;
import com.aegisid.foundation.shared.page.SortOrder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Adapters between foundation paging contracts and Spring Data paging types. */
public final class JpaPageSupport {

  private JpaPageSupport() {}

  public static PageRequest toPageRequest(PageQuery pageQuery, Sort defaultSort) {
    Objects.requireNonNull(pageQuery, "pageQuery must not be null");
    Sort sort = toSort(pageQuery.sort(), defaultSort);
    return PageRequest.of(pageQuery.toInternalPageIndex(), pageQuery.size(), sort);
  }

  public static Sort toSort(List<SortOrder> sortOrders, Sort defaultSort) {
    if (sortOrders == null || sortOrders.isEmpty()) {
      return defaultSort == null ? Sort.unsorted() : defaultSort;
    }
    List<Sort.Order> orders =
        sortOrders.stream()
            .map(
                sortOrder ->
                    switch (sortOrder.direction()) {
                      case ASC -> Sort.Order.asc(sortOrder.field());
                      case DESC -> Sort.Order.desc(sortOrder.field());
                    })
            .toList();
    return Sort.by(orders);
  }

  public static <S, T> PageResult<T> toPageResult(
      Page<S> page, PageQuery pageQuery, Function<? super S, T> mapper) {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(pageQuery, "pageQuery must not be null");
    Objects.requireNonNull(mapper, "mapper must not be null");
    List<T> items = page.getContent().stream().map(mapper).toList();
    return new PageResult<>(
        items,
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        pageQuery.sort());
  }
}
