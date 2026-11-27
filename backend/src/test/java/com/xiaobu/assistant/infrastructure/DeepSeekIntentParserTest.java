package com.xiaobu.assistant.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class DeepSeekIntentParserTest {
  // 验证正式供应商适配器的 HTTP 和 JSON 契约；只使用内存传输与测试数据。
  @Test
  void parsesOnlyCompleteReadPlans() {
    var json = JsonMapper.builder().findAndAddModules().build();
    var http = RestClient.builder();
    var server = MockRestServiceServer.bindTo(http).build();
    var parser = new DeepSeekIntentParser(json, "fixture", "test-model", "return json", http);
    var today = LocalDate.of(2026, 11, 26);
    String plan =
        """
        {"plan":{"from":"2026-11-01","through":"2026-11-26","category":"餐饮","account":null,
        "direction":"EXPENSE","recordedByMe":false,"keyword":null}}
        """;
    String analysis =
        """
        {"plan":{"from":null,"through":null,"category":null,"account":null,"direction":null,
        "recordedByMe":false,"keyword":null,"analysis":{"dataset":"accounts","metric":"balanceCents",
        "operation":"rank","groupBy":"name","limit":3,"baselineFrom":null,"baselineThrough":null,"filters":{}}}}
        """;
    for (var content :
        List.of(
            plan,
            plan.replace("\"keyword\":null", "\"keyword\":null,\"unknownFilter\":1"),
            analysis,
            analysis.replace("\"limit\":3", "\"limit\":3.5"),
            "{}")) {
      var reply =
          Map.of(
              "id",
              "example",
              "object",
              "chat.completion",
              "created",
              1,
              "model",
              "test-model",
              "choices",
              List.of(
                  Map.of(
                      "index",
                      0,
                      "finish_reason",
                      "stop",
                      "message",
                      Map.of("role", "assistant", "content", content))));
      server
          .expect(requestTo("https://api.deepseek.com/chat/completions"))
          .andExpect(header("Authorization", "Bearer fixture"))
          .andRespond(withSuccess(json.writeValueAsString(reply), MediaType.APPLICATION_JSON));
    }
    server
        .expect(requestTo("https://api.deepseek.com/chat/completions"))
        .andRespond(withServerError());
    var result = parser.parse("本月吃饭花多少", null, today);
    assertEquals("餐饮", result.category());
    assertEquals(today, result.through());
    assertNull(parser.parse("另一个条件", result, today));
    var ranked = parser.parse("有哪些账户余额排前三", null, today);
    assertEquals("accounts", ranked.analysis().dataset());
    assertEquals(3, ranked.analysis().limit());
    assertNull(parser.parse("不合法的个数", ranked, today));
    var failure = assertThrows(FoundationException.class, () -> parser.parse("协议校验", null, today));
    assertEquals("模型返回的查询协议无效", failure.getMessage());
    var outage = assertThrows(FoundationException.class, () -> parser.parse("服务故障", null, today));
    assertEquals("暂时无法解析问题，请稍后重试", outage.getMessage());
    server.verify();
  }
}
