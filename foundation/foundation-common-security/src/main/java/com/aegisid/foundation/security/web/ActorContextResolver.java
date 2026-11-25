package com.aegisid.foundation.security.web;

import com.aegisid.foundation.security.actor.ActorContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface ActorContextResolver {

  Optional<ActorContext> resolve(HttpServletRequest request);
}
