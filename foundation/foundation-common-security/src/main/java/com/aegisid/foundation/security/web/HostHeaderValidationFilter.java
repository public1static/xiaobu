package com.aegisid.foundation.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;

public class HostHeaderValidationFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!validHeaderValues(request.getHeaders("Host"))
        || !validCommaSeparatedHeaderValues(request.getHeaders("X-Forwarded-Host"))
        || !validForwardedHeaderValues(request.getHeaders("Forwarded"))) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "INVALID_HOST_HEADER");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static boolean validHeaderValues(Enumeration<String> values) {
    for (String value : Collections.list(values)) {
      if (!validHostPort(value)) {
        return false;
      }
    }
    return true;
  }

  private static boolean validCommaSeparatedHeaderValues(Enumeration<String> values) {
    for (String header : Collections.list(values)) {
      for (String value : header.split(",")) {
        if (!validHostPort(value)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean validForwardedHeaderValues(Enumeration<String> values) {
    for (String header : Collections.list(values)) {
      for (String entry : header.split(",")) {
        for (String parameter : entry.split(";")) {
          String[] pair = parameter.trim().split("=", 2);
          if (pair.length == 2
              && "host".equalsIgnoreCase(pair[0].trim())
              && !validHostPort(pair[1])) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean validHostPort(String value) {
    String hostPort = unquote(value == null ? "" : value.trim());
    if (hostPort.isBlank() || containsUnsafeCharacter(hostPort)) {
      return false;
    }
    String host;
    String port = "";
    if (hostPort.startsWith("[")) {
      int end = hostPort.indexOf(']');
      if (end <= 1) {
        return false;
      }
      host = hostPort.substring(1, end);
      String remainder = hostPort.substring(end + 1);
      if (!remainder.isEmpty()) {
        if (!remainder.startsWith(":")) {
          return false;
        }
        port = remainder.substring(1);
      }
      return validIpv6Literal(host) && validPort(port);
    }

    int colon = hostPort.lastIndexOf(':');
    if (colon >= 0) {
      if (hostPort.indexOf(':') != colon) {
        return false;
      }
      host = hostPort.substring(0, colon);
      port = hostPort.substring(colon + 1);
    } else {
      host = hostPort;
    }
    return validDnsOrIpv4Host(host) && validPort(port);
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static boolean containsUnsafeCharacter(String value) {
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isISOControl(character) || Character.isWhitespace(character)) {
        return true;
      }
    }
    return false;
  }

  private static boolean validIpv6Literal(String host) {
    return !host.isBlank()
        && host.length() <= 45
        && host.indexOf(':') >= 0
        && host.chars()
            .allMatch(
                character ->
                    Character.digit(character, 16) >= 0 || character == ':' || character == '.');
  }

  private static boolean validDnsOrIpv4Host(String host) {
    String normalized = host.toLowerCase(Locale.ROOT);
    if (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.isBlank() || normalized.length() > 253) {
      return false;
    }
    for (String label : normalized.split("\\.")) {
      if (label.isBlank() || label.length() > 63) {
        return false;
      }
      if (label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
        return false;
      }
      for (int index = 0; index < label.length(); index++) {
        char character = label.charAt(index);
        if (!Character.isLetterOrDigit(character) && character != '-') {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean validPort(String port) {
    if (port.isEmpty()) {
      return true;
    }
    if (port.length() > 5 || !port.chars().allMatch(Character::isDigit)) {
      return false;
    }
    int value = Integer.parseInt(port);
    return value >= 1 && value <= 65535;
  }
}
