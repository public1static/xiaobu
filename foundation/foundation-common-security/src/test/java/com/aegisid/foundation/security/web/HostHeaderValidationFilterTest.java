package com.aegisid.foundation.security.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HostHeaderValidationFilterTest {

  private final HostHeaderValidationFilter filter = new HostHeaderValidationFilter();

  @Test
  void allowsValidHostHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/login/session");
    request.addHeader("Host", "login.example.com:8443");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertEquals(request, chain.getRequest());
  }

  @Test
  void rejectsMalformedHostHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/login/session");
    request.addHeader("Host", "evil.com:abc");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(400, response.getStatus());
  }

  @Test
  void rejectsMalformedForwardedHostHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/login/session");
    request.addHeader("Host", "login.example.com");
    request.addHeader("X-Forwarded-Host", "login.example.com, bad host.example");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(400, response.getStatus());
  }

  @Test
  void rejectsMalformedRfcForwardedHostParameter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/login/session");
    request.addHeader("Host", "login.example.com");
    request.addHeader("Forwarded", "for=192.0.2.60;proto=https;host=\"bad host.example\"");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(400, response.getStatus());
  }
}
