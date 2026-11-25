package com.aegisid.foundation.events;

/** Small application event publishing port for adapters that delegate to framework event buses. */
public interface ApplicationEventPublisherPort {

  void publish(Object event);
}
