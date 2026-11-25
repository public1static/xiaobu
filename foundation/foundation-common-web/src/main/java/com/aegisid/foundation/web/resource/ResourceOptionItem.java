package com.aegisid.foundation.web.resource;

public record ResourceOptionItem(String value, String label, Boolean disabled, String reason) {

  public static ResourceOptionItem enabled(String value, String label) {
    return new ResourceOptionItem(value, label, null, null);
  }
}
