package com.aegisid.foundation.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

  private static final String PAYLOAD_TOO_LARGE_BODY = "{\"error\":\"payload_too_large\"}";

  private final RequestBodySizeLimitConfig config;

  public RequestBodySizeLimitFilter() {
    this(RequestBodySizeLimitConfig.defaults());
  }

  public RequestBodySizeLimitFilter(RequestBodySizeLimitConfig config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!mayCarryRequestBody(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    long limit = config.limitFor(request.getContentType());
    if (request.getContentLengthLong() > limit || multipartFileSizeExceeds(request, limit)) {
      reject(response);
      return;
    }

    try {
      filterChain.doFilter(new LimitedRequestBodyRequest(request, limit), response);
    } catch (RequestBodyTooLargeException ex) {
      reject(response);
    }
  }

  private static boolean mayCarryRequestBody(HttpServletRequest request) {
    String method = request.getMethod();
    if (method == null) {
      return false;
    }
    return switch (method.toUpperCase(Locale.ROOT)) {
      case "POST", "PUT", "PATCH", "DELETE" -> true;
      default -> false;
    };
  }

  private static boolean multipartFileSizeExceeds(HttpServletRequest request, long limit) {
    if (!(request instanceof MultipartHttpServletRequest multipartRequest)) {
      return false;
    }
    long total = 0;
    for (List<MultipartFile> files : multipartRequest.getMultiFileMap().values()) {
      for (MultipartFile file : files) {
        total += file.getSize();
        if (total > limit) {
          return true;
        }
      }
    }
    return false;
  }

  private static void reject(HttpServletResponse response) throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.resetBuffer();
    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(PAYLOAD_TOO_LARGE_BODY);
  }

  private static final class LimitedRequestBodyRequest extends HttpServletRequestWrapper {
    private final long limit;
    private ServletInputStream inputStream;
    private BufferedReader reader;

    private LimitedRequestBodyRequest(HttpServletRequest request, long limit) {
      super(request);
      this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (reader != null) {
        throw new IllegalStateException("getReader() has already been called");
      }
      return limitedInputStream();
    }

    @Override
    public BufferedReader getReader() throws IOException {
      if (reader != null) {
        return reader;
      }
      if (inputStream != null) {
        throw new IllegalStateException("getInputStream() has already been called");
      }
      Charset charset = characterEncoding();
      reader = new BufferedReader(new InputStreamReader(limitedInputStream(), charset));
      return reader;
    }

    private ServletInputStream limitedInputStream() throws IOException {
      if (inputStream == null) {
        inputStream = new LimitedServletInputStream(super.getInputStream(), limit);
      }
      return inputStream;
    }

    private Charset characterEncoding() {
      String encoding = getCharacterEncoding();
      if (encoding == null || encoding.isBlank()) {
        return StandardCharsets.UTF_8;
      }
      return Charset.forName(encoding);
    }
  }

  private static final class LimitedServletInputStream extends ServletInputStream {
    private final ServletInputStream delegate;
    private final long limit;
    private long bytesRead;

    private LimitedServletInputStream(ServletInputStream delegate, long limit) {
      this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value != -1) {
        count(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = delegate.read(buffer, offset, length);
      if (read > 0) {
        count(read);
      }
      return read;
    }

    @Override
    public int readLine(byte[] buffer, int offset, int length) throws IOException {
      int read = delegate.readLine(buffer, offset, length);
      if (read > 0) {
        count(read);
      }
      return read;
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      delegate.setReadListener(readListener);
    }

    private void count(int bytes) {
      bytesRead += bytes;
      if (bytesRead > limit) {
        throw new RequestBodyTooLargeException();
      }
    }
  }

  private static final class RequestBodyTooLargeException extends RuntimeException {}
}
