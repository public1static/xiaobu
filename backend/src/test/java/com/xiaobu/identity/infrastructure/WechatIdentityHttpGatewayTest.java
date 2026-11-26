package com.xiaobu.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.FoundationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WechatIdentityHttpGatewayTest {

  @Test
  void exchangesCodeWithoutReturningExternalIdentityOrKey() {
    var builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
    var mock = MockRestServiceServer.bindTo(builder).build();
    mock.expect(
            requestTo(
                org.hamcrest.Matchers.startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
        .andExpect(queryParam("appid", "wx-test"))
        .andExpect(queryParam("secret", "dummy-fixture"))
        .andExpect(queryParam("js_code", "code-a"))
        .andRespond(
            withSuccess(
                """
                {"openid":"fixture-openid","session_key":"fixture-key","unionid":"fixture-union"}
                """,
                MediaType.TEXT_PLAIN));
    var gateway = new WechatIdentityHttpGateway("wx-test", "dummy-fixture", builder.build());
    String actorId = gateway.resolveActorId("code-a");
    assertEquals(46, actorId.length());
    assertFalse(actorId.contains("fixture"));
    mock.verify();
  }

  @Test
  void rejectsConsumedCodeWithoutPassingUpstreamMessageThrough() {
    var builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
    var mock = MockRestServiceServer.bindTo(builder).build();
    mock.expect(queryParam("js_code", "consumed"))
        .andRespond(
            withSuccess(
                "{\"errcode\":40163,\"errmsg\":\"private diagnostic\"}",
                MediaType.APPLICATION_JSON));
    var gateway = new WechatIdentityHttpGateway("wx-test", "dummy-fixture", builder.build());
    var failure = assertThrows(FoundationException.class, () -> gateway.resolveActorId("consumed"));
    assertEquals(ErrorCategory.UNAUTHENTICATED, failure.category());
    assertFalse(failure.getMessage().contains("private diagnostic"));
    mock.verify();
  }
}
