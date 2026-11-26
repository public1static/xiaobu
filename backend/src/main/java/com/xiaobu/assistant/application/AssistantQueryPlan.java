package com.xiaobu.assistant.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.util.Map;

public record AssistantQueryPlan(
    LocalDate from,
    LocalDate through,
    String category,
    String account,
    TransactionDirection direction,
    boolean recordedByMe,
    String keyword,
    Analysis analysis) {
  // 兼容已有会话与流水快捷入口。
  public AssistantQueryPlan(
      LocalDate from,
      LocalDate through,
      String category,
      String account,
      TransactionDirection direction,
      boolean recordedByMe,
      String keyword) {
    this(from, through, category, account, direction, recordedByMe, keyword, null);
  }

  // 比较明确给出基准区间，筛选与指标在两个期间共用。
  public record Analysis(
      String dataset,
      String operation,
      String metric,
      String groupBy,
      Integer limit,
      LocalDate baselineFrom,
      LocalDate baselineThrough,
      Map<String, String> filters) {}

  // 名称澄清后保留分析操作和另一期间。
  public AssistantQueryPlan resolved(
      String categoryName, String accountName, TransactionDirection resolvedDirection) {
    return new AssistantQueryPlan(
        from,
        through,
        categoryName,
        accountName,
        resolvedDirection,
        recordedByMe,
        keyword,
        analysis);
  }
}
