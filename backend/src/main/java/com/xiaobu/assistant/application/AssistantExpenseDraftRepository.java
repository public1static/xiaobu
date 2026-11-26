package com.xiaobu.assistant.application;

import java.time.LocalDate;
import java.util.Optional;

public interface AssistantExpenseDraftRepository {
  enum Status {
    PENDING,
    CONFIRMED,
    DISCARDED
  }

  record Fields(
      LocalDate bookedOn, Long amountCents, String categoryCode, String accountId, String note) {}

  record Draft(String id, long version, Status status, Fields fields, String transactionId) {}

  // 只从已鉴权会话内读取，当前待处理草稿至多一份。
  Optional<Draft> pending(String conversation);

  Optional<Draft> find(String conversation, String id);

  Draft create(String conversation, String id, Fields fields);

  Draft update(String conversation, String id, Fields fields);

  Draft finish(String conversation, String id, Status status, String transactionId);
}
