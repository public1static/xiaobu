package com.aegisid.foundation.observability;

import java.util.Optional;

/** Immutable application version metadata for health and diagnostic surfaces. */
public record VersionInfo(String applicationName, String applicationVersion, String buildTime) {

  private static final String DEFAULT_APPLICATION_NAME = "aegisid";

  private static final String UNKNOWN = "unknown";

  public VersionInfo {
    applicationName = valueOrDefault(applicationName, DEFAULT_APPLICATION_NAME);
    applicationVersion = valueOrDefault(applicationVersion, UNKNOWN);
    buildTime = valueOrDefault(buildTime, UNKNOWN);
  }

  public static VersionInfo defaults() {
    Package sourcePackage = VersionInfo.class.getPackage();
    String implementationVersion =
        Optional.ofNullable(sourcePackage.getImplementationVersion()).orElse(UNKNOWN);
    return new VersionInfo(DEFAULT_APPLICATION_NAME, implementationVersion, UNKNOWN);
  }

  private static String valueOrDefault(String value, String defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value;
  }
}
