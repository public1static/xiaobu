package com.aegisid.foundation.events;

import com.aegisid.foundation.shared.event.DomainEvent;
import java.util.Objects;

/** Port for publishing domain events from application and domain orchestration code. */
public interface DomainEventPublisher {

  void publish(DomainEvent event);

  default void publishAll(Iterable<? extends DomainEvent> events) {
    Objects.requireNonNull(events, "events must not be null");
    events.forEach(this::publish);
  }
}
