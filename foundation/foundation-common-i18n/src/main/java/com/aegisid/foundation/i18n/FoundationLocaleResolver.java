package com.aegisid.foundation.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/** Resolves the platform locale from X-Locale, then Accept-Language, then zh-CN. */
@Component("localeResolver")
public class FoundationLocaleResolver implements LocaleResolver {

  public static final String LOCALE_HEADER = "X-Locale";

  public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    Locale headerLocale = parse(request.getHeader(LOCALE_HEADER));
    if (headerLocale != null) {
      return headerLocale;
    }

    Locale acceptLanguageLocale = parseAcceptLanguage(request.getHeader("Accept-Language"));
    if (acceptLanguageLocale != null) {
      return acceptLanguageLocale;
    }
    return DEFAULT_LOCALE;
  }

  @Override
  public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
    throw new UnsupportedOperationException("Foundation locale is resolved from request headers");
  }

  private static Locale parseAcceptLanguage(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (String token : value.split(",")) {
      Locale locale = parse(token.split(";", 2)[0]);
      if (locale != null) {
        return locale;
      }
    }
    return null;
  }

  private static Locale parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.trim().replace('_', '-');
    Locale locale = Locale.forLanguageTag(normalized);
    if (Locale.SIMPLIFIED_CHINESE.equals(locale) || Locale.US.equals(locale)) {
      return locale;
    }
    return null;
  }
}
