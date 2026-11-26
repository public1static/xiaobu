package com.xiaobu.assistant.infrastructure;

import com.xiaobu.assistant.application.AssistantExpenseDraftRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class AssistantExpenseDraftPersistenceAdapter implements AssistantExpenseDraftRepository {
  // 草稿写操作由上层账本锁和事务串行化。
  private final AssistantExpenseDraftJpaRepository drafts;

  AssistantExpenseDraftPersistenceAdapter(AssistantExpenseDraftJpaRepository drafts) {
    this.drafts = drafts;
  }

  @Override
  public Optional<Draft> pending(String conversation) {
    return drafts
        .findByConversationIdAndStatus(conversation, Status.PENDING)
        .map(AssistantExpenseDraftEntity::snapshot);
  }

  @Override
  public Optional<Draft> find(String conversation, String id) {
    return drafts
        .findByConversationIdAndId(conversation, id)
        .map(AssistantExpenseDraftEntity::snapshot);
  }

  @Override
  public Draft create(String conversation, String id, Fields fields) {
    return drafts
        .saveAndFlush(new AssistantExpenseDraftEntity(conversation, id, fields))
        .snapshot();
  }

  @Override
  public Draft update(String conversation, String id, Fields fields) {
    var draft = drafts.findByConversationIdAndId(conversation, id).orElseThrow();
    draft.apply(fields);
    drafts.flush();
    return draft.snapshot();
  }

  @Override
  public Draft finish(String conversation, String id, Status status, String transactionId) {
    var draft = drafts.findByConversationIdAndId(conversation, id).orElseThrow();
    draft.finish(status, transactionId);
    drafts.flush();
    return draft.snapshot();
  }
}
