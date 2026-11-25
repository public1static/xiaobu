package com.aegisid.foundation.web.resource;

import com.aegisid.foundation.shared.request.RequestContextView;
import com.aegisid.foundation.web.api.ApiErrorResponse;
import com.aegisid.foundation.web.api.ApiResponse;
import com.aegisid.foundation.web.request.RequestContext;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ResourceEnvelopeAdvice implements ResponseBodyAdvice<Object> {

  private static final String INCLUDE_RESOURCES = "includeResources";
  private static final String TENANT_HEADER = "X-Tenant-Id";
  private static final String USER_HEADER = "X-User-Id";
  private static final List<String> PROTOCOL_PATH_PREFIXES =
      List.of("/.well-known/", "/oauth2/", "/login/oauth2/");
  private static final List<String> MANAGEMENT_PATH_PREFIXES =
      List.of("/actuator", "/management", "/manage");

  private final ResourceScanner resourceScanner;
  private final ResourceLoadingService resourceLoadingService;
  private final ResourceCollectorHolder resourceCollectorHolder;

  @Autowired
  public ResourceEnvelopeAdvice(
      Collection<? extends ResourceLoader<?>> resourceLoaders,
      Collection<? extends OptionsLoader> optionsLoaders,
      ResourceCollectorHolder resourceCollectorHolder) {
    this(
        new ResourceScanner(),
        new ResourceLoadingService(resourceLoaders, optionsLoaders),
        resourceCollectorHolder);
  }

  public ResourceEnvelopeAdvice(
      Collection<? extends ResourceLoader<?>> resourceLoaders,
      Collection<? extends OptionsLoader> optionsLoaders) {
    this(
        new ResourceScanner(),
        new ResourceLoadingService(resourceLoaders, optionsLoaders),
        new ResourceCollectorHolder());
  }

  ResourceEnvelopeAdvice(
      ResourceScanner resourceScanner, ResourceLoadingService resourceLoadingService) {
    this(resourceScanner, resourceLoadingService, new ResourceCollectorHolder());
  }

  ResourceEnvelopeAdvice(
      ResourceScanner resourceScanner,
      ResourceLoadingService resourceLoadingService,
      ResourceCollectorHolder resourceCollectorHolder) {
    this.resourceScanner = resourceScanner;
    this.resourceLoadingService = resourceLoadingService;
    this.resourceCollectorHolder = resourceCollectorHolder;
  }

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return JacksonJsonHttpMessageConverter.class.isAssignableFrom(converterType);
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    try {
      if (hasNoBody(request, response)) {
        return null;
      }
      if (shouldSkipEnvelope(body, request, response)) {
        return body;
      }

      if (body instanceof ApiResponse<?> apiResponse) {
        ApiResources resources =
            loadResources(apiResponse.data(), request, apiResponse.requestId());
        return apiResponse.withResources(resources);
      }
      return body;
    } finally {
      resourceCollectorHolder.clear();
    }
  }

  private boolean shouldSkipEnvelope(
      Object body, ServerHttpRequest request, ServerHttpResponse response) {
    return isNonSuccessfulStatus(response)
        || isProtocolEndpoint(request)
        || isManagementEndpoint(request)
        || body instanceof ApiErrorResponse
        || body instanceof ProblemDetail
        || body instanceof ErrorResponse;
  }

  private ApiResources loadResources(Object body, ServerHttpRequest request, String requestId) {
    if (!includeResources(request)) {
      return ApiResources.empty();
    }
    try {
      return resourceLoadingService.load(
          resourceScanner.scan(body),
          resourceCollectorHolder.currentOrEmpty(),
          resourceContext(request, requestId));
    } catch (RuntimeException ex) {
      return ApiResources.empty();
    }
  }

  private boolean includeResources(ServerHttpRequest request) {
    String value = null;
    if (request instanceof ServletServerHttpRequest servletRequest) {
      value = servletRequest.getServletRequest().getParameter(INCLUDE_RESOURCES);
    }
    return value == null || !"false".equalsIgnoreCase(value);
  }

  private boolean hasNoBody(ServerHttpRequest request, ServerHttpResponse response) {
    int status = servletStatus(response);
    return "HEAD".equals(String.valueOf(request.getMethod()))
        || (status >= 100 && status < 200)
        || status == HttpStatus.NO_CONTENT.value()
        || status == HttpStatus.NOT_MODIFIED.value();
  }

  private boolean isNonSuccessfulStatus(ServerHttpResponse response) {
    int status = servletStatus(response);
    return status < 200 || status >= 300;
  }

  private int servletStatus(ServerHttpResponse response) {
    if (response instanceof ServletServerHttpResponse servletResponse) {
      return servletResponse.getServletResponse().getStatus();
    }
    return HttpStatus.OK.value();
  }

  private boolean isProtocolEndpoint(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    for (String prefix : PROTOCOL_PATH_PREFIXES) {
      if (path.equals(prefix.substring(0, prefix.length() - 1)) || path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private boolean isManagementEndpoint(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    for (String prefix : MANAGEMENT_PATH_PREFIXES) {
      if (path.equals(prefix) || path.startsWith(prefix + "/")) {
        return true;
      }
    }
    return false;
  }

  private ResourceContext resourceContext(ServerHttpRequest request, String requestId) {
    return new ResourceContext(
        request.getHeaders().getFirst(TENANT_HEADER),
        request.getHeaders().getFirst(USER_HEADER),
        locale(request),
        Set.of(),
        requestId);
  }

  private Locale locale(ServerHttpRequest request) {
    List<Locale> locales = request.getHeaders().getAcceptLanguageAsLocales();
    if (!locales.isEmpty()) {
      return locales.get(0);
    }
    if (request instanceof ServletServerHttpRequest servletRequest) {
      return servletRequest.getServletRequest().getLocale();
    }
    return Locale.getDefault();
  }

  private String requestId(ServerHttpRequest request) {
    RequestContextView current = RequestContext.currentOrNull();
    if (current != null) {
      return current.requestId();
    }
    if (request instanceof ServletServerHttpRequest servletRequest) {
      Object requestContext =
          servletRequest.getServletRequest().getAttribute(RequestContext.ATTRIBUTE_NAME);
      if (requestContext instanceof RequestContextView context) {
        return context.requestId();
      }
    }
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      Object requestContext =
          attributes.getAttribute(RequestContext.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
      if (requestContext instanceof RequestContextView context) {
        return context.requestId();
      }
    }
    return UUID.randomUUID().toString();
  }
}
