package com.aegisid.foundation.audit;

import java.util.Objects;

/** Independent audit writing port. Critical audit writes should use BLOCKING policy. */
@FunctionalInterface
public interface AuditWriter {

  void write(AuditEvent event);

  default void write(AuditEvent event, FailurePolicy failurePolicy) {
    Objects.requireNonNull(failurePolicy, "failurePolicy must not be null");
    try {
      write(event);
    } catch (RuntimeException ex) {
      if (failurePolicy == FailurePolicy.BLOCKING) {
        throw ex;
      }
    }
  }

  enum FailurePolicy {
    BEST_EFFORT,
    BLOCKING
  }
}
