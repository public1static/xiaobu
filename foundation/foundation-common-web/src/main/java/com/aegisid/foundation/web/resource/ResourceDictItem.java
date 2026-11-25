package com.aegisid.foundation.web.resource;

public record ResourceDictItem(String label, String color, String icon, String description) {

  public static ResourceDictItem labelOnly(String label) {
    return new ResourceDictItem(label, null, null, null);
  }
}
