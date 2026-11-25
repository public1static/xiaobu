package com.aegisid.foundation.shared.event;

import java.time.Instant;
import java.util.UUID;

/** Minimal domain event contract shared by foundation and business modules. */
public interface DomainEvent {

  UUID eventId();

  Instant occurredAt();
}
