package com.xiaobu.assistant.infrastructure;

import com.xiaobu.assistant.application.AssistantConversationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AssistantConversationPersistenceAdapter implements AssistantConversationRepository {
  // 聊天按消息序号分页并以条件更新提交一个完整回合。
  private final JdbcClient jdbc;

  // 使用主业务数据源与事务。
  AssistantConversationPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void create(String id, String ledger, String owner) {
    jdbc.sql("INSERT INTO assistant_conversations VALUES (?, ?, ?, '新对话', 0, UTC_TIMESTAMP(6))")
        .params(id, ledger, owner)
        .update();
  }

  @Override
  public List<Conversation> list(String ledger, String owner, int offset) {
    return jdbc.sql(
            """
            SELECT id, title, revision FROM assistant_conversations WHERE ledger_id=? AND owner_id=?
            ORDER BY updated_at DESC, id DESC LIMIT 20 OFFSET ?
            """)
        .params(ledger, owner, offset)
        .query(Conversation.class)
        .list();
  }

  @Override
  public Optional<Conversation> find(String id, String ledger, String owner) {
    return jdbc.sql(
            "SELECT id,title,revision FROM assistant_conversations WHERE id=? AND ledger_id=? AND owner_id=?")
        .params(id, ledger, owner)
        .query(Conversation.class)
        .optional();
  }

  @Override
  public List<Turn> history(String id, long before) {
    return jdbc.sql(
            """
            SELECT sequence_no AS sequence, question, answer_json FROM assistant_turns
            WHERE conversation_id=? AND sequence_no<? ORDER BY sequence_no DESC LIMIT 20
            """)
        .params(id, before)
        .query(Turn.class)
        .list()
        .reversed();
  }

  @Override
  public Optional<Turn> findRequest(String id, String clientId) {
    return jdbc.sql(
            """
            SELECT sequence_no AS sequence, question, answer_json FROM assistant_turns
            WHERE conversation_id=? AND client_id=?
            """)
        .params(id, clientId)
        .query(Turn.class)
        .optional();
  }

  @Override
  public Optional<Turn> findTurn(String id, long sequence) {
    return jdbc.sql(
            """
            SELECT sequence_no AS sequence, question, answer_json FROM assistant_turns
            WHERE conversation_id=? AND sequence_no=?
            """)
        .params(id, sequence)
        .query(Turn.class)
        .optional();
  }

  @Override
  @Transactional
  public boolean append(
      String id, long revision, String clientId, String question, String answerJson) {
    int changed =
        jdbc.sql(
                """
                UPDATE assistant_conversations SET title=IF(revision=0,LEFT(?,40),title),
                revision=revision+1, updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=?
                """)
            .params(question, id, revision)
            .update();
    if (changed == 0) return false;
    jdbc.sql(
            "INSERT INTO assistant_turns (conversation_id,sequence_no,client_id,question,answer_json)"
                + " VALUES (?,?,?,?,?)")
        .params(id, revision + 1, clientId, question, answerJson)
        .update();
    return true;
  }
}
