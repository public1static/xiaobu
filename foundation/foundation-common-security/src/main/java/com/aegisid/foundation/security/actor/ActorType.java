package com.aegisid.foundation.security.actor;

import java.util.Locale;

public enum ActorType {
  ANONYMOUS("anonymous"),
  INTERNAL_ADMIN("internal_admin"),
  CONSOLE_USER("console_user"),
  DEVELOPER("developer"),
  END_USER("end_user");

  private final String claimValue;

  ActorType(String claimValue) {
    this.claimValue = claimValue;
  }

  public String claimValue() {
    return claimValue;
  }

  public static ActorType fromClaim(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("actor_type must not be blank");
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (ActorType actorType : values()) {
      if (actorType.claimValue.equals(normalized)) {
        return actorType;
      }
    }
    throw new IllegalArgumentException("unsupported actor_type: " + value);
  }
}
