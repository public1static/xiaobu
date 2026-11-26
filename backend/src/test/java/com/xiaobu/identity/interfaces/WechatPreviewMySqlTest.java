package com.xiaobu.identity.interfaces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xiaobu.assistant.application.AssistantIntentParser;
import com.xiaobu.assistant.application.AssistantQueryPlan;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.identity.application.WechatIdentityGateway;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    verifyPrivateAssistant(book, alphaToken, betaToken);
    assertEquals(204, send("DELETE", "/wechat-session", null, betaToken).statusCode());
    assertEquals(401, send("GET", transactions + "?month=2026-10", null, betaToken).statusCode());
    assertEquals(200, send("GET", transactions + "?month=2026-10", null, alphaToken).statusCode());
  }

  // 真实 HTTP 会话核对只读成员的问账权限、私人历史、版本与来源。
  private void verifyPrivateAssistant(String book, String alpha, String beta) throws Exception {
    String root = "/ledgers/" + book + "/assistant/conversations";
    String id = data(send("POST", root, null, beta)).get("id").asText();
    String messages = root + "/" + id + "/messages";
    String question =
        "{\"question\":\"十月餐饮多少\",\"revision\":0,\"clientId\":\"" + UUID.randomUUID() + "\"}";
    var response = send("POST", messages, question, beta);
    assertEquals(200, response.statusCode(), response.body());
    var first = data(response);
    assertEquals(3200, first.at("/answer/statement/expenseCents").asInt());
    assertEquals(1, data(send("POST", messages, question, beta)).get("sequence").asInt());
    assertEquals(1, data(send("GET", messages, null, beta)).size());
    assertEquals(
        3200, data(send("GET", messages + "/1/entries", null, beta)).get("expenseCents").asInt());
    assertEquals(404, send("GET", messages, null, alpha).statusCode());
    assertEquals(0, data(send("GET", root, null, alpha)).size());
    String follow =
        "{\"question\":\"那上个月呢\",\"replyTo\":1,\"revision\":1,\"clientId\":\""
            + UUID.randomUUID()
            + "\"}";
    var second = data(send("POST", messages, follow, beta));
    assertEquals("餐饮", second.at("/answer/scope/plan/category").asText());
    assertEquals(2, second.get("sequence").asInt());
    assertEquals(409, send("POST", messages, follow.replace("那上个月呢", "另一问题"), beta).statusCode());
    String members = "/ledgers/" + book + "/members";
    var member = data(send("GET", members, null, alpha)).get(0).get("memberId").asText();
    verifyExpenseDraft(book, id, members + "/" + member + "/role", alpha, beta);
    verifyAnalytics(book, alpha, beta);
    assertEquals(204, send("DELETE", members + "/" + member, null, alpha).statusCode());
    assertEquals(403, send("GET", messages, null, beta).statusCode());
    assertEquals(403, send("GET", messages + "/1/entries", null, beta).statusCode());
  }

  // 只读、降权、旧版本及重复确认都不得产生额外流水。
  private void verifyExpenseDraft(
      String book, String chat, String role, String owner, String writer) throws Exception {
    String drafts = "/ledgers/" + book + "/assistant/conversations/" + chat + "/drafts";
    String fields =
        """
        {"question":"昨天买菜","revision":2,"fields":{"bookedOn":"2026-10-30","categoryCode":"GROCERIES","amountCents":null}}
        """;
    assertEquals(403, send("PUT", drafts, fields, writer).statusCode());
    assertEquals(204, send("PUT", role, "{\"role\":\"CAN_RECORD\"}", owner).statusCode());
    var proposed = send("PUT", drafts, fields, writer);
    assertEquals(200, proposed.statusCode(), proposed.body());
    String id = data(proposed).get("id").asText();
    String confirm = drafts + "/" + id + "/confirm";
    assertEquals(400, send("POST", confirm, "{\"version\":0}", writer).statusCode());
    String update =
        fields
            .replace("昨天买菜", "六十八块")
            .replace("\"revision\":2", "\"revision\":3,\"version\":0,\"id\":\"" + id + "\"")
            .replace("Cents\":null", "Cents\":6800");
    assertEquals(200, send("PUT", drafts, update, writer).statusCode());
    assertEquals(404, send("GET", drafts + "?id=" + id, null, owner).statusCode());
    String month = "/ledgers/" + book + "/transactions?month=2026-10";
    assertEquals(3200, data(send("GET", month, null, writer)).get("expenseCents").asInt());
    assertEquals(409, send("POST", confirm, "{\"version\":0}", writer).statusCode());
    assertEquals(204, send("PUT", role, "{\"role\":\"READ_ONLY\"}", owner).statusCode());
    assertEquals(403, send("POST", confirm, "{\"version\":1}", writer).statusCode());
    assertEquals(204, send("PUT", role, "{\"role\":\"CAN_RECORD\"}", owner).statusCode());
    var saved = send("POST", confirm, "{\"version\":1}", writer);
    assertEquals(200, saved.statusCode(), saved.body());
    String original = data(saved).get("transactionId").asText();
    assertEquals(
        original,
        data(send("POST", confirm, "{\"version\":1}", writer)).get("transactionId").asText());
    assertEquals(
        original,
        data(send("GET", drafts + "?id=" + id, null, writer)).at("/draft/transactionId").asText());
    var rows = data(send("GET", month, null, owner));
    assertEquals(2, rows.get("totalCount").asInt());
    assertEquals(10000, rows.get("expenseCents").asInt());
  }

  // 通过正式对话入口执行合成计划，覆盖不同数据源、基期与权限。
  private void verifyAnalytics(String book, String owner, String reader) throws Exception {
    String root = "/ledgers/" + book;
    String accounts = root + "/accounts";
    String cash =
        data(send("POST", accounts, "{\"name\":\"现金\",\"openingBalanceCents\":10000}", owner))
            .get("id")
            .asText();
    assertEquals(
        200,
        send("POST", accounts, "{\"name\":\"待填账户\",\"balanceKnown\":false}", owner).statusCode());
    String card = data(send("POST", accounts, "{\"name\":\"银行卡\"}", owner)).get("id").asText();
    for (String[] row : List.of(new String[] {"09", "1000"}, new String[] {"10", "2500"})) {
      var transfer =
          Map.of(
              "sourceAccountId",
              cash,
              "targetAccountId",
              card,
              "amountCents",
              row[1],
              "bookedOn",
              "2026-" + row[0] + "-03",
              "note",
              "互转夹具");
      assertEquals(
          200,
          send("POST", root + "/account-transfers", json.writeValueAsString(transfer), owner)
              .statusCode());
    }
    // 放入最近五十条之外的基期，用于发现基于首页做统计的回归。
    for (int i = 0; i < 51; i++) {
      var transfer =
          Map.of(
              "sourceAccountId",
              cash,
              "targetAccountId",
              card,
              "amountCents",
              1,
              "bookedOn",
              "2026-11-01",
              "note",
              "分页夹具");
      assertEquals(
          200,
          send("POST", root + "/account-transfers", json.writeValueAsString(transfer), owner)
              .statusCode());
    }
    String gift =
        """
        {"direction":"INCOME","amountCents":7500,"bookedOn":"2026-10-06","categoryCode":"GIFT_RECEIVED","note":"礼金夹具","extensions":[{"type":"GIFT","operation":"UPSERT","detail":{"personName":"小陈","reason":"婚礼"}}]}
        """;
    var saved = send("POST", root + "/transactions", gift, owner);
    assertEquals(200, saved.statusCode(), saved.body());
    assertTrue(
        send("GET", root + "/assistant/query-catalog", null, owner)
            .body()
            .contains("\"name\":\"members\""));
    assertFalse(
        send("GET", root + "/assistant/query-catalog", null, reader)
            .body()
            .contains("\"name\":\"members\""));
    String conversations = root + "/assistant/conversations";
    String chat = data(send("POST", conversations, null, reader)).get("id").asText();
    String messages = conversations + "/" + chat + "/messages";
    assertTrue(
        askAnalytics(messages, reader, 0, "transfers", "compare", "amountCents", null, true)
            .contains("差额（本期减基期）15.00 元"));
    assertTrue(
        askAnalytics(messages, reader, 1, "giftActivities", "rank", "incomeCents", "person", true)
            .contains("小陈 · 75.00 元"));
    assertTrue(
        askAnalytics(messages, reader, 2, "statement", "rank", "netExpenseCents", "CATEGORY", true)
            .contains("买菜 · 68.00 元"));
    String ranking =
        askAnalytics(messages, reader, 3, "accounts", "rank", "balanceCents", "name", false);
    assertTrue(ranking.contains("现金 · 64.49 元"));
    assertTrue(ranking.contains("另有 1 个账户余额待填"));
    assertTrue(
        askAnalytics(messages, reader, 4, "categories", "list", "entryCount", null, false)
            .contains("当前快照"));
    assertTrue(
        askAnalytics(messages, reader, 5, "members", "total", "entryCount", null, false)
            .contains("无法"));
    assertTrue(
        askAnalytics(messages, reader, 6, "accounts", "compare", "balanceCents", null, true)
            .contains("无法"));
    assertTrue(
        askAnalytics(messages, reader, 7, "giftBooks", "total", "entryCount", null, false)
            .contains("没有匹配记录"));
    assertTrue(
        askAnalytics(messages, reader, 8, "giftContacts", "total", "entryCount", null, false)
            .contains("当前快照"));
    // 非法的模型期间应返回澄清并保留上一轮目录，而不是中断 HTTP 会话。
    assertTrue(
        askAnalytics(messages, reader, 9, "transfers", "total", "amountCents", null, false)
            .contains("无法"));
    var clarified = data(send("GET", messages, null, reader)).get(9);
    assertEquals("giftContacts", clarified.at("/answer/scope/plan/analysis/dataset").asText());
    for (int revision = 10; revision < 22; revision++) {
      askAnalytics(messages, reader, revision, "categories", "total", "entryCount", null, false);
    }
    var recent = data(send("GET", messages, null, reader));
    assertEquals(20, recent.size());
    for (int index = 0; index < 20; index++)
      assertEquals(index + 3, recent.get(index).get("sequence").asInt());
    var older = data(send("GET", messages + "?before=3", null, reader));
    assertEquals(2, older.size());
    assertEquals(1, older.get(0).get("sequence").asInt());
    assertEquals(2, older.get(1).get("sequence").asInt());
  }

  // 固定模型输出，将金额计算及授权验证与供应商语义评测分开。
  private String askAnalytics(
      String path,
      String token,
      int revision,
      String dataset,
      String op,
      String metric,
      String group,
      boolean dated)
      throws Exception {
    var from = dated ? LocalDate.of(2026, 10, 1) : null;
    var plan =
        new AssistantQueryPlan(
            from,
            dated ? from.plusDays(30) : null,
            null,
            null,
            null,
            false,
            null,
            new AssistantQueryPlan.Analysis(
                dataset,
                op,
                metric,
                group,
                op.equals("rank") ? 3 : null,
                op.equals("compare") ? from.minusMonths(1) : null,
                op.equals("compare") ? from.minusDays(1) : null,
                dataset.equals("transfers") ? Map.of("source", "现金") : Map.of()));
    var mapper = JsonMapper.builder().findAndAddModules().build();
    var body =
        Map.of(
            "question",
            mapper.writeValueAsString(plan),
            "revision",
            revision,
            "clientId",
            UUID.randomUUID());
    var reply = send("POST", path, json.writeValueAsString(body), token);
    assertEquals(200, reply.statusCode(), reply.body());
    return data(reply).at("/answer/text").asText();
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
    // 用受控解析输出验证账务结果，测试不向供应商发送数据。
    @Bean
    @Primary
    AssistantIntentParser fixtureParser() {
      return (question, previous, today) ->
          question.startsWith("{")
              ? JsonMapper.builder()
                  .findAndAddModules()
                  .build()
                  .readValue(question, AssistantQueryPlan.class)
              : new AssistantQueryPlan(
                  LocalDate.of(2026, 10, 1),
                  LocalDate.of(2026, 10, 31),
                  "餐饮",
                  null,
                  TransactionDirection.EXPENSE,
                  false,
                  null);
    }

    @Bean
    @Primary
    WechatIdentityGateway fixtureIdentities() {
      // 每次启动使用独立身份，同一测试中的重复登录仍回到同一账本。
      var run = UUID.randomUUID().toString();
      return code -> (code.equals("B") ? "wx_b_" : "wx_a_") + run;
    }
  }
}
