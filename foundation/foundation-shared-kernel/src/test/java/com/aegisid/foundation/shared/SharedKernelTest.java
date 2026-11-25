package com.aegisid.foundation.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.aegisid.foundation.shared.id.TypedId;
import com.aegisid.foundation.shared.page.PageQuery;
import com.aegisid.foundation.shared.page.PageResult;
import com.aegisid.foundation.shared.page.SortDirection;
import com.aegisid.foundation.shared.page.SortOrder;
import com.aegisid.foundation.shared.request.RequestContextView;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedKernelTest {

  @Test
  void typedIdRejectsBlankValues() {
    assertThrows(IllegalArgumentException.class, () -> new TypedId<>(" "));
  }

  @Test
  void typedIdTrimsValues() {
    assertEquals("entity_1", new TypedId<>(" entity_1 ").value());
  }

  @Test
  void pageQueryUsesOneBasedExternalPageNumbers() {
    PageQuery query = new PageQuery(1, 20);

    assertEquals(1, query.page());
    assertEquals(20, query.size());
    assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, 20));
  }

  @Test
  void pageQueryConvertsToZeroBasedInternalPageIndex() {
    PageQuery query = new PageQuery(3, 20);

    assertEquals(2, query.toInternalPageIndex());
  }

  @Test
  void pageQueryRejectsSizeOutsideSupportedBounds() {
    assertThrows(IllegalArgumentException.class, () -> new PageQuery(1, 0));
    assertThrows(IllegalArgumentException.class, () -> new PageQuery(1, 201));
  }

  @Test
  void pageQueryCarriesExternalSortOrdersAsImmutableValues() {
    PageQuery query = new PageQuery(1, 20, List.of(SortOrder.desc("createdAt")));

    assertEquals(List.of(SortOrder.desc("createdAt")), query.sort());
    assertThrows(
        UnsupportedOperationException.class, () -> query.sort().add(SortOrder.asc("name")));
  }

  @Test
  void sortOrderRejectsInvalidExternalFieldNames() {
    assertThrows(IllegalArgumentException.class, () -> SortOrder.asc(" "));
    assertThrows(NullPointerException.class, () -> new SortOrder("createdAt", null));
  }

  @Test
  void pageResultDerivesTotalPagesFromTotalElementsAndSize() {
    PageResult<String> result = new PageResult<>(List.of("alpha", "beta"), 1, 20, 41);

    assertEquals(41, result.totalElements());
    assertEquals(3, result.totalPages());
  }

  @Test
  void pageResultRejectsSuppliedTotalPagesThatDoNotMatchElementsAndSize() {
    PageResult<String> result = new PageResult<>(List.of("alpha"), 1, 20, 41, 3);

    assertEquals(3, result.totalPages());
    assertThrows(
        IllegalArgumentException.class, () -> new PageResult<>(List.of("alpha"), 1, 20, 41, 2));
  }

  @Test
  void pageResultUsesZeroTotalPagesWhenThereAreNoElements() {
    PageResult<String> result = new PageResult<>(List.of(), 1, 20, 0);

    assertEquals(0, result.totalPages());
  }

  @Test
  void pageResultRejectsInvalidStates() {
    assertThrows(NullPointerException.class, () -> new PageResult<String>(null, 1, 20, 0));
    assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 0, 20, 0));
    assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 1, 201, 0));
    assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 1, 20, -1));
  }

  @Test
  void pageResultItemsAreImmutable() {
    PageResult<String> result = new PageResult<>(List.of("alpha"), 1, 20, 1);

    assertThrows(UnsupportedOperationException.class, () -> result.items().add("beta"));
  }

  @Test
  void pageResultCarriesSortOrdersAsImmutableValues() {
    PageResult<String> result =
        new PageResult<>(
            List.of("alpha"), 1, 20, 1, 1, List.of(new SortOrder("name", SortDirection.ASC)));

    assertEquals(List.of(SortOrder.asc("name")), result.sort());
    assertThrows(
        UnsupportedOperationException.class, () -> result.sort().add(SortOrder.desc("createdAt")));
  }

  @Test
  void pageResultCanMapItemsWithoutLosingPagingMetadata() {
    PageResult<String> result =
        new PageResult<>(List.of("alpha"), 2, 20, 21, 2, List.of(SortOrder.asc("name")));

    PageResult<Integer> mapped = result.map(String::length);

    assertEquals(List.of(5), mapped.items());
    assertEquals(2, mapped.page());
    assertEquals(20, mapped.size());
    assertEquals(21, mapped.totalElements());
    assertEquals(2, mapped.totalPages());
    assertEquals(List.of(SortOrder.asc("name")), mapped.sort());
  }

  @Test
  void foundationExceptionExposesStableErrorCode() {
    ErrorCode errorCode = new ErrorCode("FOUNDATION-TEST-0001");
    FoundationException exception = new FoundationException(errorCode, "test failure");

    assertEquals(errorCode, exception.errorCode());
    assertEquals("FOUNDATION-TEST-0001", exception.errorCode().value());
    assertEquals(ErrorCategory.INVALID_ARGUMENT, exception.category());
    assertTrue(exception.details().isEmpty());
  }

  @Test
  void foundationExceptionCarriesCategoryAndImmutableSafeDetails() {
    FoundationException exception =
        new FoundationException(
            new ErrorCode("FOUNDATION-TEST-DETAILS"),
            ErrorCategory.NOT_FOUND,
            "not found",
            java.util.Map.of("resourceType", "tenant"));

    assertEquals(ErrorCategory.NOT_FOUND, exception.category());
    assertEquals(java.util.Map.of("resourceType", "tenant"), exception.details());
    assertThrows(
        UnsupportedOperationException.class, () -> exception.details().put("resourceType", "user"));
  }

  @Test
  void errorCodeRejectsNullOrBlankValues() {
    assertThrows(IllegalArgumentException.class, () -> new ErrorCode(null));
    assertThrows(IllegalArgumentException.class, () -> new ErrorCode(" "));
  }

  @Test
  void foundationExceptionPreservesCauseAndRejectsNullErrorCode() {
    RuntimeException cause = new RuntimeException("root cause");
    ErrorCode errorCode = new ErrorCode("FOUNDATION-TEST-0002");

    FoundationException exception = new FoundationException(errorCode, "test failure", cause);

    assertSame(cause, exception.getCause());
    assertThrows(NullPointerException.class, () -> new FoundationException(null, "test failure"));
    assertThrows(
        NullPointerException.class, () -> new FoundationException(null, "test failure", cause));
  }

  @Test
  void requestContextViewExposesRequestIdAndTraceIdAsReadOnlyJavaInterface() {
    assertTrue(RequestContextView.class.isInterface());

    Method[] methods = RequestContextView.class.getDeclaredMethods();
    assertEquals(2, methods.length);
    assertTrue(hasStringGetter(methods, "requestId"));
    assertTrue(hasStringGetter(methods, "traceId"));
    assertFalse(Arrays.stream(methods).anyMatch(SharedKernelTest::usesServletOrSpringWebType));
  }

  private static boolean hasStringGetter(Method[] methods, String name) {
    return Arrays.stream(methods)
        .anyMatch(
            method ->
                name.equals(method.getName())
                    && method.getParameterCount() == 0
                    && String.class.equals(method.getReturnType())
                    && Modifier.isPublic(method.getModifiers()));
  }

  private static boolean usesServletOrSpringWebType(Method method) {
    return isServletOrSpringWebType(method.getReturnType())
        || Arrays.stream(method.getParameterTypes())
            .anyMatch(SharedKernelTest::isServletOrSpringWebType);
  }

  private static boolean isServletOrSpringWebType(Class<?> type) {
    return type.getName().startsWith("jakarta.servlet.")
        || type.getName().startsWith("javax.servlet.")
        || type.getName().startsWith("org.springframework.web.");
  }
}
