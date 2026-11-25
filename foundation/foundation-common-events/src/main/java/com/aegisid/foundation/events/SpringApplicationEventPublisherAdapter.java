package com.aegisid.foundation.events;

import com.aegisid.foundation.shared.event.DomainEvent;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;

/** Spring-backed event publisher adapter for generic application and domain events. */
public final class SpringApplicationEventPublisherAdapter
    implements ApplicationEventPublisherPort, DomainEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public SpringApplicationEventPublisherAdapter(
      ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher =
        Objects.requireNonNull(
            applicationEventPublisher, "applicationEventPublisher must not be null");
  }

  @Override
  public void publish(DomainEvent event) {
    publish((Object) event);
  }

  @Override
  public void publish(Object event) {
    applicationEventPublisher.publishEvent(Objects.requireNonNull(event, "event must not be null"));
  }
}
