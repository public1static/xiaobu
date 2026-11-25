package com.aegisid.foundation.web.error;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.FoundationException;
import com.aegisid.foundation.web.api.ApiErrorResponse;
import com.aegisid.foundation.web.request.RequestIds;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts foundation exceptions into the standard API error envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  public static final String INTERNAL_ERROR_CODE = "FOUNDATION-INTERNAL-ERROR";

  public static final String INTERNAL_ERROR_MESSAGE = "Internal server error";

  public static final String BAD_REQUEST_CODE = "FOUNDATION_BAD_REQUEST";

  public static final String BAD_REQUEST_MESSAGE = "Invalid request";

  private final ApiErrorMessageResolver messageResolver;

  public GlobalExceptionHandler() {
    this((MessageSource) null);
  }

  public GlobalExceptionHandler(MessageSource messageSource) {
    this.messageResolver = new ApiErrorMessageResolver(messageSource);
  }

  @Autowired
  public GlobalExceptionHandler(ObjectProvider<MessageSource> messageSource) {
    this(messageSource.getIfAvailable());
  }

  @ExceptionHandler(FoundationException.class)
  public ResponseEntity<ApiErrorResponse> handleFoundationException(FoundationException exception) {
    String requestId = RequestIds.currentOrGenerate();
    logFoundationException(exception, requestId);
    ApiErrorResponse body =
        ApiErrorResponse.failure(
            exception.errorCode().value(),
            resolveMessage(exception),
            requestId,
            exception.details());
    return ResponseEntity.status(statusFor(exception.category())).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception) {
    String requestId = RequestIds.currentOrGenerate();
    List<Map<String, String>> violations =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
            .toList();
    LOGGER.warn("request_validation_failed requestId={} violations={}", requestId, violations);
    return badRequest(requestId, Map.of("violations", violations));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
      ConstraintViolationException exception) {
    String requestId = RequestIds.currentOrGenerate();
    List<Map<String, String>> violations =
        exception.getConstraintViolations().stream()
            .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
            .map(
                violation ->
                    Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
            .toList();
    LOGGER.warn("request_constraint_violation requestId={} violations={}", requestId, violations);
    return badRequest(requestId, Map.of("violations", violations));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception) {
    String requestId = RequestIds.currentOrGenerate();
    LOGGER.warn("malformed_request requestId={} message={}", requestId, exception.getMessage());
    return badRequest(requestId, Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
    String requestId = RequestIds.currentOrGenerate();
    LOGGER.error("unhandled_exception requestId={}", requestId, exception);
    ApiErrorResponse body =
        ApiErrorResponse.failure(
            INTERNAL_ERROR_CODE,
            messageResolver.resolve(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE),
            requestId,
            Map.of());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  private ResponseEntity<ApiErrorResponse> badRequest(String requestId, Map<String, ?> details) {
    ApiErrorResponse body =
        ApiErrorResponse.failure(
            BAD_REQUEST_CODE,
            messageResolver.resolve(BAD_REQUEST_CODE, BAD_REQUEST_MESSAGE),
            requestId,
            details);
    return ResponseEntity.badRequest().body(body);
  }

  private String resolveMessage(FoundationException exception) {
    String fallbackCode = "FOUNDATION_" + exception.category().name();
    String safeFallback = messageResolver.resolve(fallbackCode, "Request could not be processed");
    return messageResolver.resolve(exception.errorCode().value(), safeFallback);
  }

  private static void logFoundationException(FoundationException exception, String requestId) {
    if (exception.category() == ErrorCategory.UPSTREAM_FAILURE
        || exception.category() == ErrorCategory.SERVICE_UNAVAILABLE
        || exception.category() == ErrorCategory.INTERNAL) {
      LOGGER.error(
          "foundation_exception code={} category={} requestId={} message={}",
          exception.errorCode().value(),
          exception.category(),
          requestId,
          exception.getMessage(),
          exception);
      return;
    }
    LOGGER.warn(
        "foundation_exception code={} category={} requestId={} message={}",
        exception.errorCode().value(),
        exception.category(),
        requestId,
        exception.getMessage());
  }

  public static HttpStatus statusFor(ErrorCategory category) {
    return switch (category) {
      case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
      case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
      case RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT;
      case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case UPSTREAM_FAILURE -> HttpStatus.BAD_GATEWAY;
      case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
