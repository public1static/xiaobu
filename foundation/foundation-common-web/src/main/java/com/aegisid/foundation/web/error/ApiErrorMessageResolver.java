package com.aegisid.foundation.web.error;

import com.aegisid.foundation.shared.text.Texts;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/** Resolves user-facing API error messages from stable error codes. */
public final class ApiErrorMessageResolver {

  private static final String ERROR_MESSAGE_KEY_PREFIX = "error.";

  private final MessageSource messageSource;

  public ApiErrorMessageResolver(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  public String resolve(String errorCode, String fallbackMessage) {
    if (messageSource != null && Texts.hasText(errorCode)) {
      String message =
          messageSource.getMessage(
              ERROR_MESSAGE_KEY_PREFIX + errorCode, null, null, LocaleContextHolder.getLocale());
      if (Texts.hasText(message)) {
        return message;
      }
    }
    if (Texts.hasText(fallbackMessage)) {
      return fallbackMessage;
    }
    return errorCode;
  }
}
