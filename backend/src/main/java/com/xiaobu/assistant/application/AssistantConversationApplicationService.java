package com.xiaobu.assistant.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.application.LedgerAccountApplicationService;
import com.xiaobu.ledger.application.LedgerCategoryApplicationService;
import com.xiaobu.ledger.application.LedgerStatementFilter;
import com.xiaobu.ledger.application.LedgerStatementPageResult;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("local-expense | wechat-preview")
public class AssistantConversationApplicationService {
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerTransactionApplicationService transactions;
  private final LedgerCategoryApplicationService categories;
  private final LedgerAccountApplicationService accounts;
  private final AssistantConversationRepository conversations;
  private final AssistantAnalyticsApplicationService analytics;
  private final AssistantIntentParser parser;
  private final EntityIdGenerator ids;
  private final JsonMapper json;

  // 用户消息与模型调用分离于写事务；只有版本匹配的完整问答可以追加。
  AssistantConversationApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerTransactionApplicationService transactions,
      LedgerCategoryApplicationService categories,
      LedgerAccountApplicationService accounts,
      AssistantConversationRepository conversations,
      AssistantAnalyticsApplicationService analytics,
      AssistantIntentParser parser,
      EntityIdGenerator ids,
      JsonMapper json) {
    this.ledgers = ledgers;
    this.transactions = transactions;
    this.categories = categories;
    this.accounts = accounts;
    this.conversations = conversations;
    this.analytics = analytics;
    this.parser = parser;
    this.ids = ids;
    this.json = json;
  }

  // 同一账本的成员分别拥有独立会话。
  public AssistantConversationRepository.Conversation createConversation(
      String ledger, String actor) {
    ledgers.requireReadAccess(ledger, actor);
    var id = ids.next(new IdPrefix("chat"));
    conversations.create(id, ledger, actor);
    return new AssistantConversationRepository.Conversation(id, "新对话", 0);
  }

  // 返回最近使用的会话，分页偏移不接受负数。
  public List<AssistantConversationRepository.Conversation> listConversations(
      String ledger, String actor, int offset) {
    ledgers.requireReadAccess(ledger, actor);
    if (offset < 0) throw invalid("历史页码无效");
    return conversations.list(ledger, actor, offset);
  }

  // 历史消息保存当时显示的金额与顺序。
  public List<Message> listMessages(String ledger, String actor, String id, long before) {
    requireConversation(ledger, actor, id);
    return conversations.history(id, before).stream().map(this::message).toList();
  }

  // 同一次请求重发时返回之前的消息；不同提问通过会话版本避免上下文串用。
  public Message askQuestion(
      String ledger,
      String actor,
      String id,
      String question,
      long revision,
      String clientId,
      Long replyTo) {
    var conversation = requireConversation(ledger, actor, id);
    var previousRequest = conversations.findRequest(id, clientId);
    if (previousRequest.isPresent()) {
      if (!previousRequest.get().question().equals(question)) throw changed();
      return message(previousRequest.get());
    }
    if (conversation.revision() != revision) throw changed();
    var anchor = replyTo == null ? revision : replyTo;
    Answer prior = anchor == 0 ? null : answer(id, anchor);
    Answer result = resolve(ledger, actor, question, prior);
    requireConversation(ledger, actor, id);
    if (!conversations.append(id, revision, clientId, question, json.writeValueAsString(result)))
      throw changed();
    return new Message(revision + 1, question, result);
  }

  // 从选中卡片的原始条件钻取最新明细。
  public LedgerStatementPageResult listMessageEntries(
      String ledger, String actor, String id, long sequence, int page) {
    requireConversation(ledger, actor, id);
    var card = answer(id, sequence);
    if (card.scope() == null || card.statement() == null) throw invalid("请选择有流水来源的回答");
    return statement(ledger, actor, card.scope(), page);
  }

  // 先解析用户引用的卡片和候选项，再按需要调用模型。
  private Answer resolve(String ledger, String actor, String text, Answer prior) {
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    Scope previous = prior == null ? null : prior.scope();
    if (prior != null && prior.choices() != null) {
      for (var choice : prior.choices()) {
        if (choice.label().equals(text)) return executePlan(ledger, actor, choice.plan(), previous);
      }
    }
    if (text.equals("看第二笔") || text.equals("第二笔呢")) {
      if (prior == null || prior.statement() == null || prior.statement().entries().size() < 2)
        return new Answer("所选回答中没有第二笔流水。", previous, null, null);
      var row = prior.statement().entries().get(1);
      String source = row.originalTransactionId() == null ? row.id() : row.originalTransactionId();
      transactions.getLedgerTransaction(ledger, actor, source);
      return new Answer("已找到所选回答的第二笔流水。", previous, null, source);
    }
    AssistantQueryPlan plan;
    if (text.equals("本月餐饮支出") || text.equals("本月收支")) {
      boolean dining = text.equals("本月餐饮支出");
      plan =
          new AssistantQueryPlan(
              today.withDayOfMonth(1),
              today,
              dining ? "餐饮" : null,
              null,
              dining ? TransactionDirection.EXPENSE : null,
              false,
              null);
    } else if (text.equals("那上个月呢") && previous != null && previous.plan().analysis() == null) {
      var last = today.withDayOfMonth(1).minusDays(1);
      var p = previous.plan();
      var updated =
          new Scope(
              new AssistantQueryPlan(
                  last.withDayOfMonth(1),
                  last,
                  p.category(),
                  p.account(),
                  p.direction(),
                  p.recordedByMe(),
                  p.keyword()),
              previous.categoryCode(),
              previous.accountId());
      return summarize(ledger, actor, updated);
    } else {
      plan =
          parser.parse(
              text,
              previous == null ? null : previous.plan(),
              today,
              AssistantDataset.catalog(ledgers.isOwner(ledger, actor)));
    }
    if (plan == null) return new Answer("请说明要查的数据、筛选条件或统计指标。", previous, null, null);
    return executePlan(ledger, actor, plan, previous);
  }

  // 将模型返回的名称限制在当前账本目录内；选项保留本轮的其他条件。
  private Answer executePlan(String ledger, String actor, AssistantQueryPlan plan, Scope previous) {
    String category = null;
    if (plan.category() != null) {
      var matches =
          categories.listLedgerCategories(ledger, actor).stream()
              .filter(c ->
                  c.name().equals(plan.category())
                      && (plan.direction() == null || c.direction() == plan.direction()))
              .toList();
      if (matches.size() > 1) {
        var choices =
            matches.stream()
                .map(c ->
                    new Choice(
                        (c.direction() == TransactionDirection.INCOME ? "收入" : "支出")
                            + " · "
                            + c.name(),
                        plan.resolved(c.name(), plan.account(), c.direction())))
                .toList();
        return new Answer("请选择分类的收支方向。", previous, null, null, choices);
      }
      if (matches.isEmpty()) return new Answer("账本中没有匹配的分类，请核对名称后重试。", previous, null, null);
      category = matches.getFirst().code();
    }
    String account = null;
    if (plan.account() != null) {
      var catalog = accounts.listLedgerAccounts(ledger, actor);
      var matches = catalog.stream().filter(a -> a.name().equals(plan.account())).toList();
      if (matches.size() != 1) {
        var choices =
            catalog.stream()
                .limit(8)
                .map(a ->
                    new Choice(
                        "账户 · " + a.name(),
                        plan.resolved(plan.category(), a.name(), plan.direction())))
                .toList();
        return new Answer("没有匹配到该账户，请核对名称或选择本账本账户。", previous, null, null, choices);
      }
      account = matches.getFirst().id();
    }
    var scope = new Scope(plan, category, account);
    if (plan.analysis() != null) {
      var result = analytics.evaluate(ledger, actor, plan, category, account);
      return result
          .map(text -> new Answer(text, scope, null, null))
          .orElseGet(() -> new Answer("当前目录无法按这些条件查询，请核对数据、指标、期间与筛选条件。", previous, null, null));
    }
    return summarize(ledger, actor, scope);
  }

  // 读取业务服务给出的完整期间汇总与有序第一页。
  private Answer summarize(String ledger, String actor, Scope scope) {
    var result = statement(ledger, actor, scope, 0);
    return new Answer(result.totalCount() == 0 ? "没有匹配的流水。" : "已按当前账本统计。", scope, result, null);
  }

  // 历史卡使用已解析的 ID，重命名不会把明细指向另一个同名目录项。
  private LedgerStatementPageResult statement(String ledger, String actor, Scope scope, int page) {
    var p = scope.plan();
    return transactions.listLedgerTransactions(
        new ListLedgerTransactionsQuery(
            ledger,
            actor,
            p.from(),
            p.through(),
            p.keyword(),
            page,
            20,
            new LedgerStatementFilter(
                scope.categoryCode(),
                scope.accountId(),
                p.direction(),
                p.recordedByMe(),
                null,
                false),
            null));
  }

  // 每次访问同时检查账本可见性和私人会话归属。
  private AssistantConversationRepository.Conversation requireConversation(
      String ledger, String actor, String id) {
    ledgers.requireReadAccess(ledger, actor);
    return conversations
        .find(id, ledger, actor)
        .orElseThrow(() ->
            new FoundationException(
                new ErrorCode("XIAOBU_CHAT_UNAVAILABLE"),
                ErrorCategory.NOT_FOUND,
                "对话不可用",
                Map.of()));
  }

  // 引用只能在已鉴权的会话内解析。
  private Answer answer(String id, long sequence) {
    return message(conversations.findTurn(id, sequence).orElseThrow(() -> invalid("找不到引用的回答")))
        .answer();
  }

  // 将持久化的问答快照恢复为客户端卡片。
  private Message message(AssistantConversationRepository.Turn row) {
    return new Message(
        row.sequence(), row.question(), json.readValue(row.answerJson(), Answer.class));
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_CHAT_INVALID"), message);
  }

  private static FoundationException changed() {
    return new FoundationException(
        new ErrorCode("XIAOBU_CHAT_CHANGED"), ErrorCategory.CONFLICT, "对话已更新，请刷新后重试", Map.of());
  }

  public record Scope(AssistantQueryPlan plan, String categoryCode, String accountId) {}

  public record Choice(String label, AssistantQueryPlan plan) {}

  public record Answer(
      String text,
      Scope scope,
      LedgerStatementPageResult statement,
      String sourceId,
      List<Choice> choices) {
    // 无需用户澄清的回答直接返回金额卡或来源引用。
    Answer(String text, Scope scope, LedgerStatementPageResult statement, String sourceId) {
      this(text, scope, statement, sourceId, List.of());
    }
  }

  public record Message(long sequence, String question, Answer answer) {}
}
