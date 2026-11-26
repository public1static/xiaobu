package com.xiaobu.assistant.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
    for (var content :
        List.of(plan, plan.replace("\"keyword\":null", "\"keyword\":null,\"unknownFilter\":1"))) {
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
    var result = parser.parse("本月吃饭花多少", null, today);
    assertEquals("餐饮", result.category());
    assertEquals(today, result.through());
    assertNull(parser.parse("另一个条件", result, today));
    server.verify();
  }
}
