package com.xiaobu.assistant.application;

import java.time.LocalDate;
import java.util.List;

public interface AssistantIntentParser {
  // 返回完整查询条件；无法可靠表达的问题用空计划触发澄清。
  AssistantQueryPlan parse(String question, AssistantQueryPlan previous, LocalDate today);

  // 将本轮可用目录传给模型，测试解析器仍可只接收业务输入。
  default AssistantQueryPlan parse(
      String q,
      AssistantQueryPlan previous,
      LocalDate today,
      List<AssistantDataset.Definition> catalog) {
    return parse(q, previous, today);
  }
}
