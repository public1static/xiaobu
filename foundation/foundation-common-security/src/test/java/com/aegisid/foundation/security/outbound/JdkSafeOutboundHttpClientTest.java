package com.aegisid.foundation.security.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class JdkSafeOutboundHttpClientTest {

  @Test
  void returnsUtf8BodyForAllowedJsonResponse() throws Exception {
    FakeHttpClient transport =
        new FakeHttpClient(
            200, Map.of("content-type", List.of("application/json")), "{\"ok\":true}");
    JdkSafeOutboundHttpClient client =
        new JdkSafeOutboundHttpClient(publicAddressValidator(), transport);

    SafeOutboundResponse response =
        client.execute(
            SafeOutboundRequest.get(
                URI.create("https://idp.example.com/.well-known/openid-configuration"),
                SafeOutboundPolicy.json()));

    assertEquals(200, response.statusCode());
    assertEquals("{\"ok\":true}", response.body());
    assertEquals("GET", transport.lastRequest.method());
  }

  @Test
  void rejectsRedirectResponseWhenPolicyDisallowsRedirects() throws Exception {
    FakeHttpClient transport =
        new FakeHttpClient(302, Map.of("location", List.of("https://idp.example.com/next")), "");
    JdkSafeOutboundHttpClient client =
        new JdkSafeOutboundHttpClient(publicAddressValidator(), transport);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                client.execute(
                    SafeOutboundRequest.get(
                        URI.create("https://idp.example.com/.well-known/openid-configuration"),
                        SafeOutboundPolicy.json())));

    assertEquals("Safe outbound response redirect is not allowed", exception.getMessage());
  }

  @Test
  void acceptsRedirectResponseWhenPolicyAllowsRedirects() throws Exception {
    FakeHttpClient transport =
        new FakeHttpClient(302, Map.of("location", List.of("https://idp.example.com/next")), "");
    JdkSafeOutboundHttpClient client =
        new JdkSafeOutboundHttpClient(publicAddressValidator(), transport);

    SafeOutboundResponse response =
        client.execute(
            SafeOutboundRequest.get(
                URI.create("https://idp.example.com/oauth2/authorize"),
                SafeOutboundPolicy.webhookAllowingRedirects()));

    assertEquals(302, response.statusCode());
  }

  private static SafeOutboundAddressValidator publicAddressValidator() throws Exception {
    InetAddress address = InetAddress.getByName("93.184.216.34");
    return new SafeOutboundAddressValidator(host -> new InetAddress[] {address});
  }

  private static final class FakeHttpClient extends HttpClient {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private HttpRequest lastRequest;

    private FakeHttpClient(int statusCode, Map<String, List<String>> headers, String body) {
      this.statusCode = statusCode;
      this.headers = headers;
      this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      return null;
    }

    @Override
    public SSLParameters sslParameters() {
      return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      lastRequest = request;
      return (HttpResponse<T>) new FixedResponse(statusCode, headers, body, request);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
      throw new UnsupportedOperationException("sendAsync is not used by this test");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      throw new UnsupportedOperationException("sendAsync is not used by this test");
    }
  }

  private record FixedResponse(
      int statusCode, Map<String, List<String>> rawHeaders, byte[] body, HttpRequest request)
      implements HttpResponse<byte[]> {

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(rawHeaders, (name, value) -> true);
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return request.uri();
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }
}
