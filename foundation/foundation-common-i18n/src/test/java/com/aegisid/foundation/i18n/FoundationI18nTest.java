package com.aegisid.foundation.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Component;

class FoundationI18nTest {

  @Test
  void xLocaleTakesPrecedenceOverAcceptLanguage() {
    FoundationLocaleResolver resolver = new FoundationLocaleResolver();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(FoundationLocaleResolver.LOCALE_HEADER, "en-US");
    request.addHeader("Accept-Language", "zh-CN");

    assertEquals(Locale.US, resolver.resolveLocale(request));
  }

  @Test
  void localeResolverIsRegisteredWithSpringMvcBeanNameWhenScanned() {
    Component component =
        AnnotationUtils.findAnnotation(FoundationLocaleResolver.class, Component.class);

    assertEquals("localeResolver", component.value());
  }

  @Test
  void invalidXLocaleFallsBackToAcceptLanguage() {
    FoundationLocaleResolver resolver = new FoundationLocaleResolver();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(FoundationLocaleResolver.LOCALE_HEADER, "fr-FR");
    request.addHeader("Accept-Language", "en-US");

    assertEquals(Locale.US, resolver.resolveLocale(request));
  }

  @Test
  void underscoreLocaleFormatIsSupported() {
    FoundationLocaleResolver resolver = new FoundationLocaleResolver();
    MockHttpServletRequest englishRequest = new MockHttpServletRequest();
    englishRequest.addHeader(FoundationLocaleResolver.LOCALE_HEADER, "en_US");
    MockHttpServletRequest chineseRequest = new MockHttpServletRequest();
    chineseRequest.addHeader(FoundationLocaleResolver.LOCALE_HEADER, "zh_CN");

    assertEquals(Locale.US, resolver.resolveLocale(englishRequest));
    assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(chineseRequest));
  }

  @Test
  void acceptLanguageIsUsedWhenXLocaleMissing() {
    FoundationLocaleResolver resolver = new FoundationLocaleResolver();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "en-US,en;q=0.9");

    assertEquals(Locale.US, resolver.resolveLocale(request));
  }

  @Test
  void acceptLanguageSkipsUnsupportedLocalesUntilSupportedLocale() {
    FoundationLocaleResolver resolver = new FoundationLocaleResolver();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "fr-FR,zh-CN;q=0.9,en-US;q=0.8");

    assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(request));
  }

  @Test
  void defaultLocaleIsZhCn() {
    FoundationLocaleResolver resolver = new FoundationLocaleResolver();
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(request));
  }

  @Test
  void messageKeyResolvesForZhCnAndEnUs() {
    ReloadableResourceBundleMessageSource messageSource =
        new ReloadableResourceBundleMessageSource();
    messageSource.setBasename("classpath:messages");
    messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
    messageSource.setFallbackToSystemLocale(false);

    assertEquals(
        "操作成功",
        messageSource.getMessage("foundation.api.success", null, Locale.SIMPLIFIED_CHINESE));
    assertEquals(
        "Success", messageSource.getMessage("foundation.api.success", null, Locale.ENGLISH));
    assertEquals("Success", messageSource.getMessage("foundation.api.success", null, Locale.US));
  }

  @Test
  void loginRiskMessageKeysResolveToReadableText() {
    ReloadableResourceBundleMessageSource messageSource =
        new ReloadableResourceBundleMessageSource();
    messageSource.setBasename("classpath:messages");
    messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
    messageSource.setFallbackToSystemLocale(false);

    assertEquals(
        "请先完成图形验证码后继续操作。",
        messageSource.getMessage("error.risk_captcha_required", null, Locale.SIMPLIFIED_CHINESE));
    assertEquals(
        "登录失败次数过多，请稍后再试。",
        messageSource.getMessage(
            "error.login_attempts_throttled", null, Locale.SIMPLIFIED_CHINESE));
  }

  @Test
  void autoConfigurationProvidesUtf8MessageSource() {
    MessageSource messageSource = new FoundationI18nAutoConfiguration().messageSource();

    assertEquals(
        "内部服务错误",
        messageSource.getMessage(
            "error.FOUNDATION-INTERNAL-ERROR", null, Locale.SIMPLIFIED_CHINESE));
    assertEquals(
        "Internal server error",
        messageSource.getMessage("error.FOUNDATION-INTERNAL-ERROR", null, Locale.US));
  }

  @Test
  void i18nAutoConfigurationIsRegisteredForSpringBootDiscovery() throws IOException {
    String importLocation =
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(importLocation)) {
      assertNotNull(stream);

      String imports = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(FoundationI18nAutoConfiguration.class.getName(), imports.trim());
    }
  }
}
