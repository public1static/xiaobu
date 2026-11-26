package com.xiaobu.assistant.interfaces;

import com.xiaobu.ledger.domain.TransactionDirection;
import com.xiaobu.ledger.interfaces.LedgerStatementPageResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

record AssistantMessageResponse(long sequence, String question, AnswerResponse answer) {
  record AnswerResponse(
      String text,
      ScopeResponse scope,
      LedgerStatementPageResponse statement,
      String sourceId,
      List<ChoiceResponse> choices) {}

  record ScopeResponse(QueryPlanResponse plan, String categoryCode, String accountId) {}

  record ChoiceResponse(String label, QueryPlanResponse plan) {}

  record QueryPlanResponse(
      LocalDate from,
      LocalDate through,
      String category,
      String account,
      TransactionDirection direction,
      boolean recordedByMe,
      String keyword,
      AnalysisResponse analysis) {}

  record AnalysisResponse(
      String dataset,
      String operation,
      String metric,
      String groupBy,
      Integer limit,
      LocalDate baselineFrom,
      LocalDate baselineThrough,
      Map<String, String> filters) {}
}
