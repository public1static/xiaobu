package com.aegisid.foundation.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Thread-local tenant context holder. Always clear it at request or job boundaries. */
public final class TenantContextHolder {

  private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

  private TenantContextHolder() {}

  public static Optional<TenantContext> currentTenant() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static void set(TenantContext tenantContext) {
    CURRENT.set(Objects.requireNonNull(tenantContext, "tenantContext must not be null"));
  }

  public static void clear() {
    CURRENT.remove();
  }

  public static Scope withTenant(TenantContext tenantContext) {
    TenantContext previous = CURRENT.get();
    set(tenantContext);
    return new Scope(previous);
  }

  public static void runWithTenant(TenantContext tenantContext, Runnable runnable) {
    Objects.requireNonNull(runnable, "runnable must not be null");
    try (Scope ignored = withTenant(tenantContext)) {
      runnable.run();
    }
  }

  public static <T> T callWithTenant(TenantContext tenantContext, Callable<T> callable)
      throws Exception {
    Objects.requireNonNull(callable, "callable must not be null");
    try (Scope ignored = withTenant(tenantContext)) {
      return callable.call();
    }
  }

  public static final class Scope implements AutoCloseable {

    private final TenantContext previous;
    private boolean closed;

    private Scope(TenantContext previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      if (previous == null) {
        clear();
      } else {
        CURRENT.set(previous);
      }
      closed = true;
    }
  }
}
