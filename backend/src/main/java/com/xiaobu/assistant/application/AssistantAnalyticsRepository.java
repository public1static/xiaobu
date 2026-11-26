package com.xiaobu.assistant.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AssistantAnalyticsRepository {
  record Value(String label, BigDecimal value, long count) {}

  // 返回数据库中完整期间的统计，排行或明细在排序后截取。
  List<Value> query(
      String ledger,
      String actor,
      AssistantQueryPlan plan,
      String categoryCode,
      String accountId,
      LocalDate from,
      LocalDate through);
}
