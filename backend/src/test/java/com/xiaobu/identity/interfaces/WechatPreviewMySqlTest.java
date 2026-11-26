package com.xiaobu.identity.interfaces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.identity.application.WechatIdentityGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(
    named = "XIAOBU_DEV_DB_URL",
    matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/xiaobu_expense_test(?:\\?.*)?")
@SpringBootTest(
    classes = {XiaobuApplication.class, WechatPreviewMySqlTest.Fixture.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"xiaobu.wechat.app-id=wx-test", "xiaobu.wechat.app-secret=fixture-only"})
@ActiveProfiles("wechat-preview")
class WechatPreviewMySqlTest {

  private final HttpClient client = HttpClient.newHttpClient();
  private final JsonMapper json = new JsonMapper();

  @LocalServerPort private int port;
  @Autowired private ApplicationContext context;

  @Test
  void sharesOnlyAfterConsentAndKeepsWechatSessionsSeparate() throws Exception {
    assertTrue(context.getBeansOfType(LocalSessionController.class).isEmpty());
    assertEquals(401, send("GET", "/wechat-session", null, null).statusCode());
    var alpha = signIn("A");
    var beta = signIn("B");
    String alphaToken = alpha.get("token").asText();
    String betaToken = beta.get("token").asText();
    String book = alpha.get("ledgerId").asText();
    assertNotEquals(book, beta.get("ledgerId").asText());
    assertEquals(book, signIn("A2").get("ledgerId").asText());
    assertEquals(
        "no-store",
        send("GET", "/wechat-session", null, alphaToken)
            .headers()
            .firstValue("Cache-Control")
            .orElseThrow());
    assertFalse(send("GET", "/wechat-session", null, alphaToken).body().contains(alphaToken));

    String transactions = "/ledgers/" + book + "/transactions";
    var draft =
        """
        {"direction":"EXPENSE","amountCents":3200,"bookedOn":"2026-10-31","categoryCode":"DINING","note":"联调午饭"}
        """;
    assertEquals(200, send("POST", transactions, draft, alphaToken).statusCode());
    String catalog = "/ledgers/" + book + "/assistant/query-catalog";
    assertEquals(403, send("GET", catalog, null, betaToken).statusCode());
    var invitation = data(send("POST", "/ledgers/" + book + "/invitations", null, alphaToken));
    String invite = "{\"token\":\"" + invitation.get("token").asText() + "\"}";
    assertEquals(200, send("POST", "/ledger-invitations/preview", invite, null).statusCode());
    assertEquals(403, send("GET", transactions + "?month=2026-10", null, betaToken).statusCode());
    assertEquals(200, send("POST", "/ledger-invitations/accept", invite, betaToken).statusCode());
    var statement = data(send("GET", transactions + "?month=2026-10", null, betaToken));
    assertEquals(
        "statement", data(send("GET", catalog, null, betaToken)).get(0).get("name").asText());
    String query =
        transactions + "?month=2026-10&direction=EXPENSE&categoryCode=DINING&groupBy=CATEGORY";
    assertEquals(
        400,
        send("GET", transactions + "?month=2026-10&groupBy=UNKNOWN", null, betaToken).statusCode());
    var grouped = data(send("GET", query, null, betaToken));
    assertEquals(3200, grouped.get("groups").get(0).get("netExpenseCents").asInt());
    assertEquals(
        0,
        data(send("GET", query + "&recordedByMe=true", null, betaToken)).get("totalCount").asInt());
    assertEquals(1, statement.get("totalCount").asInt());
    assertEquals(3200, statement.get("expenseCents").asInt());
    assertEquals(3200, statement.get("entries").get(0).get("amountCents").asInt());
    assertEquals(403, send("POST", transactions, draft, betaToken).statusCode());
    assertEquals(204, send("DELETE", "/wechat-session", null, betaToken).statusCode());
    assertEquals(401, send("GET", transactions + "?month=2026-10", null, betaToken).statusCode());
    assertEquals(200, send("GET", transactions + "?month=2026-10", null, alphaToken).statusCode());
  }

  private JsonNode signIn(String code) throws Exception {
    var response = send("POST", "/wechat-session", "{\"code\":\"" + code + "\"}", null);
    assertEquals(200, response.statusCode());
    return data(response);
  }

  private JsonNode data(HttpResponse<String> response) {
    return json.readTree(response.body()).get("data");
  }

  private HttpResponse<String> send(String method, String path, String body, String token)
      throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request.header("Content-Type", "application/json");
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @TestConfiguration
  static class Fixture {
    @Bean
    @Primary
    WechatIdentityGateway fixtureIdentities() {
      // 每次启动使用独立身份，同一测试中的重复登录仍回到同一账本。
      var run = java.util.UUID.randomUUID().toString();
      return code -> (code.equals("B") ? "wx_b_" : "wx_a_") + run;
    }
  }
}
