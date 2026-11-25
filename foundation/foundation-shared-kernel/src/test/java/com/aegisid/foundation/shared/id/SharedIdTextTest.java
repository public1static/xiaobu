package com.aegisid.foundation.shared.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SharedIdTextTest {

  @Test
  void tenantIdsRequireTrimmedTenantId() {
    assertEquals("tenant-1", TenantIds.require(" tenant-1 "));
    assertEquals("tenantId must not be blank", assertBlankMessage(() -> TenantIds.require(" ")));
  }

  @Test
  void userIdsRequireTrimmedUserId() {
    assertEquals("user-1", UserIds.require(" user-1 "));
    assertEquals("userId must not be blank", assertBlankMessage(() -> UserIds.require(" ")));
  }

  @Test
  void userPoolIdsRequireTrimmedUserPoolId() {
    assertEquals("pool-1", UserPoolIds.require(" pool-1 "));
    assertEquals(
        "userPoolId must not be blank", assertBlankMessage(() -> UserPoolIds.require(" ")));
  }

  private static String assertBlankMessage(Runnable operation) {
    return assertThrows(IllegalArgumentException.class, operation::run).getMessage();
  }
}
