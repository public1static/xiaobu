package com.xiaobu.assistant.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xiaobu.assistant.application.AssistantDataset;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "XIAOBU_EVALUATE_DEEPSEEK", matches = "true")
class DeepSeekCatalogEvaluationIntegrationTest {
  // 留出题只发送合成问题与结构目录，使用正式适配器、提示词和配置的模型。
  @Test
  void interpretsHeldOutQuestionsAndPreservesContext() throws Exception {
    String key = System.getenv("DEEPSEEK_API_KEY");
    assertTrue(key != null && !key.isBlank(), "请在服务端环境中配置模型密钥");
    var parser =
        new DeepSeekIntentParser(
            JsonMapper.builder().findAndAddModules().build(),
            key,
            System.getenv().getOrDefault("XIAOBU_DEEPSEEK_MODEL", "deepseek-v4-flash"),
            new ClassPathResource("assistant/statement-plan.txt"));
    var today = LocalDate.of(2026, 11, 26);
    var catalog = AssistantDataset.catalog(false);
    var rank = parser.parse("现在哪两个账户余额最高？", null, today, catalog);
    assertNotNull(rank);
    assertNull(rank.from());
    assertEquals("accounts", rank.analysis().dataset());
    assertEquals("rank", rank.analysis().operation());
    assertEquals("balanceCents", rank.analysis().metric());
    assertEquals("name", rank.analysis().groupBy());
    assertEquals(2, rank.analysis().limit());
    var diff = parser.parse("比较十月整月与九月整月的账户互转总金额，以九月为基期。", null, today, catalog);
    assertNotNull(diff);
    assertEquals("transfers", diff.analysis().dataset());
    assertEquals("amountCents", diff.analysis().metric());
    assertEquals("compare", diff.analysis().operation());
    assertEquals(LocalDate.of(2026, 10, 1), diff.from());
    assertEquals(LocalDate.of(2026, 10, 31), diff.through());
    assertEquals(LocalDate.of(2026, 9, 1), diff.analysis().baselineFrom());
    assertEquals(LocalDate.of(2026, 9, 30), diff.analysis().baselineThrough());
    var follow = parser.parse("两个月都只算从现金转出的，其他条件不变", diff, today, catalog);
    assertNotNull(follow);
    assertEquals(diff.from(), follow.from());
    assertEquals(diff.through(), follow.through());
    assertEquals(diff.analysis().baselineFrom(), follow.analysis().baselineFrom());
    assertEquals(diff.analysis().baselineThrough(), follow.analysis().baselineThrough());
    assertEquals("现金", follow.analysis().filters().get("source"));
    assertEquals("compare", follow.analysis().operation());
    assertNull(parser.parse("去年元旦当天有哪些成员？", null, today, catalog));
  }
}
