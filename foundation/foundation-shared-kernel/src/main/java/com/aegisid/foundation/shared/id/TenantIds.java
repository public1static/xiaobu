package com.aegisid.foundation.shared.id;

import com.aegisid.foundation.shared.text.Texts;

public final class TenantIds {

  private TenantIds() {}

  public static String require(String value) {
    return Texts.require(value, "tenantId");
  }
}
