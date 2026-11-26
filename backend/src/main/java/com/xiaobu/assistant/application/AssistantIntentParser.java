package com.xiaobu.assistant.application;

import java.time.LocalDate;

public interface AssistantIntentParser {
  // 返回完整查询条件；无法可靠表达的问题用空计划触发澄清。
  AssistantQueryPlan parse(String question, AssistantQueryPlan previous, LocalDate today);
}
