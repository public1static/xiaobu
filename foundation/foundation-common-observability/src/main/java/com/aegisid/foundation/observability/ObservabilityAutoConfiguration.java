package com.aegisid.foundation.observability;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Registers observability runtime components for Spring Boot applications. */
@AutoConfiguration
@ConditionalOnClass(Filter.class)
public class ObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RequestContextMdcContributor requestContextMdcContributor() {
    return new RequestContextMdcContributor();
  }

  @Bean
  @ConditionalOnMissingBean
  public RequestLoggingFilter requestLoggingFilter(RequestContextMdcContributor mdcContributor) {
    return new RequestLoggingFilter(mdcContributor);
  }
}
