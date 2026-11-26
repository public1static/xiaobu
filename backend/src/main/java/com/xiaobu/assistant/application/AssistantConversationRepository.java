package com.xiaobu.assistant.application;

import java.util.List;
import java.util.Optional;

public interface AssistantConversationRepository {
  record Conversation(String id, String title, long revision) {}

  record Turn(long sequence, String question, String answerJson) {}

  // 新对话归属于当前用户和当前账本。
  void create(String id, String ledger, String owner);

  // 只列出本人创建的会话，最近使用的在前。
  List<Conversation> list(String ledger, String owner, int offset);

  // 归属条件与编号同时检查，防止引用他人的消息。
  Optional<Conversation> find(String id, String ledger, String owner);

  // 读取指定消息之前的一页历史，返回聊天展示顺序。
  List<Turn> history(String id, long before);

  // 取本次回答所引用的精确消息。
  Optional<Turn> findTurn(String id, long sequence);

  // 断线重发可查回同一次请求的结果。
  Optional<Turn> findRequest(String id, String clientId);

  // 原子推进会话版本，迟到的计算结果不能覆盖新一轮。
  boolean append(String id, long revision, String clientId, String question, String answerJson);
}
