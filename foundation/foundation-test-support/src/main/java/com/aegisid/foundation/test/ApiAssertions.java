package com.aegisid.foundation.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.web.api.ApiErrorResponse;
import com.aegisid.foundation.web.api.ApiResponse;

/** Assertions for foundation API response envelopes. */
public final class ApiAssertions {

  private ApiAssertions() {}

  public static <T> void assertSuccess(ApiResponse<T> response, String requestId) {
    assertNotNull(response, "response must not be null");
    assertTrue(response.success(), "success response must be marked successful");
    assertEquals(ApiResponse.SUCCESS_CODE, response.code());
    assertEquals(requestId, response.requestId());
    assertNotNull(response.timestamp(), "timestamp must be present");
  }

  public static void assertError(ApiErrorResponse response, String code, String requestId) {
    assertNotNull(response, "response must not be null");
    assertFalse(response.success(), "error response must not be marked successful");
    assertEquals(code, response.code());
    assertEquals(requestId, response.requestId());
    assertNotNull(response.timestamp(), "timestamp must be present");
    assertNotNull(response.details(), "details must not be null");
  }
}
