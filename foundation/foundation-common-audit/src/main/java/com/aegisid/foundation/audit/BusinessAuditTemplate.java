package com.aegisid.foundation.audit;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class BusinessAuditTemplate {
  private static final String RESULT_KEY = "result";
  private static final String FAILURE_REASON_KEY = "failure_reason";

  private final AuditWriter auditWriter;
  private final Clock clock;

  public BusinessAuditTemplate(AuditWriter auditWriter, Clock clock) {
    this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public <T> T execute(BusinessAuditCommand command, Supplier<T> action) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(action, "action must not be null");
    try {
      T result = action.get();
      write(command, "success", Map.of());
      return result;
    } catch (RuntimeException exception) {
      write(command, "failure", Map.of(FAILURE_REASON_KEY, failureReason(exception)));
      throw exception;
    }
  }

  public void run(BusinessAuditCommand command, Runnable action) {
    Objects.requireNonNull(action, "action must not be null");
    execute(
        command,
        () -> {
          action.run();
          return null;
        });
  }

  private void write(
      BusinessAuditCommand command, String result, Map<String, String> additionalDetails) {
    Map<String, String> details = new LinkedHashMap<>(command.details());
    details.put(RESULT_KEY, result);
    details.putAll(additionalDetails);
    auditWriter.write(
        AuditEventFactory.createAt(
            clock.instant(),
            command.tenantId(),
            command.actorId().orElse(AuditEventFactory.DEFAULT_ACTOR_ID),
            command.action(),
            command.resourceType(),
            command.resourceId(),
            details),
        command.failurePolicy());
  }

  private static String failureReason(RuntimeException exception) {
    return exception.getMessage() == null
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }
}
