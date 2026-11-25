package com.aegisid.foundation.web.resource;

import java.time.Duration;
import java.util.List;

public interface OptionsLoader {

  default String resourceType() {
    return "options";
  }

  default String defaultDomain() {
    return "";
  }

  default Duration cacheTtl() {
    return Duration.ZERO;
  }

  List<ResourceOptionItem> load(String domain, ResourceContext context);
}
