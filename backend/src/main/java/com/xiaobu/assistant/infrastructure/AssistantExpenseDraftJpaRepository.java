package com.xiaobu.assistant.infrastructure;

import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Status;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantExpenseDraftJpaRepository
    extends JpaRepository<AssistantExpenseDraftEntity, String> {
  Optional<AssistantExpenseDraftEntity> findByConversationIdAndStatus(
      String conversationId, Status status);

  Optional<AssistantExpenseDraftEntity> findByConversationIdAndId(String conversationId, String id);
}
