package com.aegisid.foundation.security.web;

import com.aegisid.foundation.security.actor.ActorContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CompositeActorContextResolver implements ActorContextResolver {

  private final List<ActorContextResolver> resolvers;

  public CompositeActorContextResolver(List<ActorContextResolver> resolvers) {
    Objects.requireNonNull(resolvers, "resolvers must not be null");
    this.resolvers = List.copyOf(resolvers);
  }

  @Override
  public Optional<ActorContext> resolve(HttpServletRequest request) {
    for (ActorContextResolver resolver : resolvers) {
      Optional<ActorContext> actor = resolver.resolve(request);
      if (actor.isPresent()) {
        return actor;
      }
    }
    return Optional.empty();
  }
}
