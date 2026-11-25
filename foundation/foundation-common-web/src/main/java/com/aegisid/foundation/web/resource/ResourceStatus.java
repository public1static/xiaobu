package com.aegisid.foundation.web.resource;

import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle status for display resources. */
public enum ResourceStatus {
  ACTIVE("active"),
  DELETED("deleted"),
  MISSING("missing");

  private final String value;

  ResourceStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }
}
