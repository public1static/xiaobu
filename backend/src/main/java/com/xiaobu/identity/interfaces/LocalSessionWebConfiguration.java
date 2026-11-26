package com.xiaobu.identity.interfaces;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

@Configuration(proxyBeanMethods = false)
@Profile("local-expense & !wechat-preview")
class LocalSessionWebConfiguration implements WebMvcConfigurer {

  LocalSessionWebConfiguration(@Value("${server.address:}") String address) {
    if (!"127.0.0.1".equals(address)) {
      throw new IllegalStateException("本地会话预览必须绑定到 127.0.0.1");
    }
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    var noStore = new WebContentInterceptor();
    noStore.addCacheMapping(CacheControl.noStore(), "/api/v1/**");
    registry.addInterceptor(noStore);
  }
}
