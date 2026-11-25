package com.aegisid.foundation.web.resource;

public record ResourceEnumItem(String label, String color, String icon, String description) {

  public static ResourceEnumItem labelOnly(String label) {
    return new ResourceEnumItem(label, null, null, null);
  }
}
