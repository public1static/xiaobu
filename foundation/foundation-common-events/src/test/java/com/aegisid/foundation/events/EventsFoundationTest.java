package com.aegisid.foundation.events;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.aegisid.foundation.shared.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventsFoundationTest {

  @Test
  void domainEventPublisherCanPublishDomainEvents() {
    CapturingApplicationEventPublisher applicationPublisher =
        new CapturingApplicationEventPublisher();
    DomainEventPublisher publisher =
        new SpringApplicationEventPublisherAdapter(applicationPublisher);
    TestDomainEvent domainEvent =
        new TestDomainEvent(UUID.randomUUID(), Instant.parse("2026-04-25T01:02:03Z"));

    publisher.publish(domainEvent);

    assertSame(domainEvent, applicationPublisher.publishedEvent);
  }

  private static final class CapturingApplicationEventPublisher
      implements org.springframework.context.ApplicationEventPublisher {

    private Object publishedEvent;

    @Override
    public void publishEvent(Object event) {
      this.publishedEvent = event;
    }
  }

  private record TestDomainEvent(UUID eventId, Instant occurredAt) implements DomainEvent {}
}
