package com.aegisid.foundation.security.outbound;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class JdkSafeOutboundHttpClient implements SafeOutboundHttpClient {

  private final SafeOutboundAddressValidator addressValidator;
  private final HttpClient httpClient;

  public JdkSafeOutboundHttpClient() {
    this(
        new SafeOutboundAddressValidator(),
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build());
  }

  public JdkSafeOutboundHttpClient(SafeOutboundAddressValidator addressValidator) {
    this(
        addressValidator,
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build());
  }

  JdkSafeOutboundHttpClient(SafeOutboundAddressValidator addressValidator, HttpClient httpClient) {
    this.addressValidator =
        Objects.requireNonNull(addressValidator, "addressValidator must not be null");
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
  }

  @Override
  public SafeOutboundResponse execute(SafeOutboundRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    addressValidator.validate(request.uri());
    HttpRequest httpRequest = toHttpRequest(request);
    try {
      HttpResponse<byte[]> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
      validateResponse(response, request.policy());
      return new SafeOutboundResponse(
          response.statusCode(),
          response.headers().map(),
          new String(response.body(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new IllegalStateException("Safe outbound request failed", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Safe outbound request interrupted", exception);
    }
  }

  private static HttpRequest toHttpRequest(SafeOutboundRequest request) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(request.uri()).timeout(request.policy().timeout());
    request.headers().forEach(builder::header);
    if (request.body() == null) {
      builder.method(request.method().name(), HttpRequest.BodyPublishers.noBody());
    } else {
      builder.method(
          request.method().name(),
          HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));
    }
    return builder.build();
  }

  private static void validateResponse(HttpResponse<byte[]> response, SafeOutboundPolicy policy) {
    if (!policy.allowRedirects() && response.statusCode() >= 300 && response.statusCode() < 400) {
      throw new IllegalArgumentException("Safe outbound response redirect is not allowed");
    }
    if (response.body().length > policy.maxResponseBytes()) {
      throw new IllegalArgumentException("Safe outbound response exceeded max size");
    }
    if (policy.allowedContentTypePrefixes().isEmpty()) {
      return;
    }
    String contentType = firstHeader(response.headers().map(), "content-type");
    if (contentType == null) {
      throw new IllegalArgumentException("Safe outbound response content type is missing");
    }
    String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
    boolean allowed =
        policy.allowedContentTypePrefixes().stream()
            .map(prefix -> prefix.toLowerCase(Locale.ROOT))
            .anyMatch(normalizedContentType::startsWith);
    if (!allowed) {
      throw new IllegalArgumentException("Safe outbound response content type is not allowed");
    }
  }

  private static String firstHeader(Map<String, List<String>> headers, String name) {
    List<String> values = headers.get(name);
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.getFirst();
  }
}
