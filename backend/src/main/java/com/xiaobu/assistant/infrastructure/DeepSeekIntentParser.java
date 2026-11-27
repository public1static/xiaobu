package com.xiaobu.assistant.infrastructure;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.assistant.application.AssistantDataset;
import com.xiaobu.assistant.application.AssistantIntentParser;
import com.xiaobu.assistant.application.AssistantQueryPlan;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class DeepSeekIntentParser implements AssistantIntentParser {
  // 未配置凭据时模型保持关闭，演示查询仍可使用。
  private final DeepSeekChatModel model;
  // 版本化的只读协议，无工具调用和写操作。
  private final String instruction;
  // 使用项目 Jackson 配置解析日期和枚举。
  private final JsonMapper json;

  // 供应商地址固定在服务端，限制交互请求的连接与读取时长。
  @Autowired
  DeepSeekIntentParser(
      JsonMapper json,
      @Value("${DEEPSEEK_API_KEY:}") String key,
      @Value("${XIAOBU_DEEPSEEK_MODEL:deepseek-v4-flash}") String name,
      @Value("classpath:assistant/statement-plan.txt") Resource prompt)
      throws IOException {
    this(json, key, name, prompt.getContentAsString(StandardCharsets.UTF_8), httpClient());
  }

  // 为同步模型调用设置有限超时。
  private static RestClient.Builder httpClient() {
    var http = new SimpleClientHttpRequestFactory();
    http.setConnectTimeout(Duration.ofSeconds(3));
    http.setReadTimeout(Duration.ofSeconds(15));
    return RestClient.builder().requestFactory(http);
  }

  // 使用可注入的 HTTP 客户端验证供应商响应契约。
  DeepSeekIntentParser(
      JsonMapper json, String key, String name, String prompt, RestClient.Builder client) {
    this.json = json;
    this.instruction = prompt;
    if (key.isBlank()) {
      model = null;
      return;
    }
    var api = DeepSeekApi.builder().apiKey(key).restClientBuilder(client).build();
    var options =
        DeepSeekChatOptions.builder()
            .model(name)
            .disableThinking()
            .maxTokens(500)
            .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
            .build();
    model =
        DeepSeekChatModel.builder()
            .deepSeekApi(api)
            .options(options)
            .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
            .build();
  }

  // 只发送问题与必要的查询上下文，不发送来源流水或成员资料。
  @Override
  public AssistantQueryPlan parse(String question, AssistantQueryPlan previous, LocalDate today) {
    return parse(question, previous, today, AssistantDataset.catalog(false));
  }

  // 提供当前授权目录的结构，不向供应商发送目录数据。
  @Override
  public AssistantQueryPlan parse(
      String question,
      AssistantQueryPlan previous,
      LocalDate today,
      List<AssistantDataset.Definition> catalog) {
    if (model == null) {
      throw unavailable("尚未配置文字模型，可以使用页面上的快捷问法");
    }
    var input = json.writeValueAsString(new Input(today, previous, question, catalog));
    String output;
    try {
      var response =
          model.call(new Prompt(List.of(new SystemMessage(instruction), new UserMessage(input))));
      var result = response.getResult();
      if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
        throw unavailable("模型返回的查询协议无效");
      }
      output = result.getOutput().getText();
    } catch (TransientAiException | NonTransientAiException | RestClientException ex) {
      throw unavailable("暂时无法解析问题，请稍后重试");
    }
    try {
      var node = json.readTree(output);
      if (node == null || !node.isObject() || node.size() != 1 || !node.has("plan")) {
        throw unavailable("模型返回的查询协议无效");
      }
      var fields = node.get("plan");
      if (fields.isNull()) {
        return null;
      }
      var allowed =
          Set.of("from", "through", "category", "account", "direction", "recordedByMe", "keyword");
      if (fields.has("analysis")) {
        allowed = new HashSet<>(allowed);
        allowed.add("analysis");
        var a = fields.get("analysis");
        if (!a.isObject()
            || !Set.of(
                    "dataset",
                    "operation",
                    "metric",
                    "groupBy",
                    "limit",
                    "baselineFrom",
                    "baselineThrough",
                    "filters")
                .equals(Set.copyOf(a.propertyNames()))) {
          return null;
        }
        for (var k : List.of("dataset", "operation", "metric")) {
          if (!a.get(k).isString()) {
            return null;
          }
        }
        for (var k : List.of("groupBy", "baselineFrom", "baselineThrough")) {
          if (!a.get(k).isNull() && !a.get(k).isString()) {
            return null;
          }
        }
        if (!a.get("limit").isNull() && !a.get("limit").isIntegralNumber()) {
          return null;
        }
        var filters = a.get("filters");
        if (!filters.isObject()
            || filters.propertyNames().stream().anyMatch(k -> !filters.get(k).isString())) {
          return null;
        }
      }
      if (!fields.isObject()
          || !Set.copyOf(fields.propertyNames()).equals(allowed)
          || !fields.get("recordedByMe").isBoolean()) {
        return null;
      }
      for (var name : List.of("category", "account", "direction", "keyword")) {
        var field = fields.get(name);
        if (!field.isNull() && (!field.isString() || field.asString().isBlank())) {
          return null;
        }
      }
      for (var k : List.of("from", "through")) {
        if (!fields.get(k).isString() && !(fields.has("analysis") && fields.get(k).isNull())) {
          return null;
        }
      }
      return json.treeToValue(fields, AssistantQueryPlan.class);
    } catch (JacksonException ex) {
      throw unavailable("模型返回的查询协议无效");
    }
  }

  // 避免上游异常及请求正文进入日志。
  private static FoundationException unavailable(String message) {
    return new FoundationException(
        new ErrorCode("XIAOBU_ASSISTANT_MODEL_UNAVAILABLE"),
        ErrorCategory.SERVICE_UNAVAILABLE,
        message,
        Map.of());
  }

  private record Input(
      LocalDate today,
      AssistantQueryPlan previous,
      String question,
      List<AssistantDataset.Definition> catalog) {}
}
