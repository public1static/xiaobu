package com.xiaobu.identity.infrastructure;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.identity.application.WechatIdentityGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("wechat-preview")
class WechatIdentityHttpGateway implements WechatIdentityGateway {

  private static final ErrorCode LOGIN_INVALID = new ErrorCode("XIAOBU_WECHAT_LOGIN_INVALID");
  private static final ErrorCode LOGIN_UNAVAILABLE = new ErrorCode("XIAOBU_WECHAT_UNAVAILABLE");

  private final String appId;
  private final String appSecret;
  private final RestClient client;

  @Autowired
  WechatIdentityHttpGateway(
      @Value("${xiaobu.wechat.app-id:}") String appId,
      @Value("${xiaobu.wechat.app-secret:}") String appSecret) {
    this(appId, appSecret, createClient());
  }

  WechatIdentityHttpGateway(String appId, String appSecret, RestClient client) {
    if (appId.isBlank() || appSecret.isBlank()) {
      throw new IllegalStateException(
          "WeChat preview requires an AppID and a server-side AppSecret");
    }
    this.appId = appId;
    this.appSecret = appSecret;
    this.client = client;
  }

  private static RestClient createClient() {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofSeconds(3));
    requests.setReadTimeout(Duration.ofSeconds(5));
    return RestClient.builder()
        .baseUrl("https://api.weixin.qq.com")
        .requestFactory(requests)
        .build();
  }

  @Override
  public String resolveActorId(String loginCode) {
    WechatIdentityResponse response;
    try {
      response =
          client
              .get()
              .uri(builder ->
                  builder
                      .path("/sns/jscode2session")
                      .queryParam("appid", appId)
                      .queryParam("secret", appSecret)
                      .queryParam("js_code", loginCode)
                      .queryParam("grant_type", "authorization_code")
                      .build())
              .retrieve()
              .body(WechatIdentityResponse.class);
    } catch (RestClientException ex) {
      // Don't attach the exception: an HTTP error can carry the credential-bearing URL.
      throw new FoundationException(
          LOGIN_UNAVAILABLE, ErrorCategory.UPSTREAM_FAILURE, "微信登录暂不可用，请稍后重试", Map.of());
    }
    if (response != null && response.errcode() != null && response.errcode() != 0) {
      if (response.errcode() == 40029 || response.errcode() == 40163) {
        throw new FoundationException(
            LOGIN_INVALID, ErrorCategory.UNAUTHENTICATED, "微信登录凭证失效，请重试", Map.of());
      }
      throw new FoundationException(
          LOGIN_UNAVAILABLE, ErrorCategory.UPSTREAM_FAILURE, "微信登录暂不可用，请稍后重试", Map.of());
    }
    if (response == null || response.openid() == null || response.openid().isBlank()) {
      throw new FoundationException(
          LOGIN_UNAVAILABLE, ErrorCategory.UPSTREAM_FAILURE, "微信登录暂不可用，请稍后重试", Map.of());
    }
    return toActorId(response.openid());
  }

  private String toActorId(String openid) {
    try {
      var hash =
          MessageDigest.getInstance("SHA-256")
              .digest((appId + ":" + openid).getBytes(StandardCharsets.UTF_8));
      return "wx_" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private record WechatIdentityResponse(String openid, Integer errcode) {}
}
