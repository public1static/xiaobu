package com.xiaobu.assistant.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Draft;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Fields;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Status;
import com.xiaobu.ledger.application.LedgerAccountApplicationService;
import com.xiaobu.ledger.application.LedgerCategoryApplicationService;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import com.xiaobu.ledger.application.RecordLedgerTransactionCommand;
import com.xiaobu.ledger.application.TransactionExtensionRegistry;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("local-expense | wechat-preview")
public class AssistantExpenseDraftApplicationService {
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerTransactionApplicationService transactions;
  private final LedgerAccountApplicationService accounts;
  private final LedgerCategoryApplicationService categories;
  private final TransactionExtensionRegistry extensions;
  private final AssistantExpenseDraftRepository drafts;
  private final AssistantConversationRepository chats;
  private final EntityIdGenerator ids;
  private final JsonMapper json;

  AssistantExpenseDraftApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerTransactionApplicationService transactions,
      LedgerAccountApplicationService accounts,
      LedgerCategoryApplicationService categories,
      TransactionExtensionRegistry extensions,
      AssistantExpenseDraftRepository drafts,
      AssistantConversationRepository chats,
      EntityIdGenerator ids,
      JsonMapper json) {
    this.ledgers = ledgers;
    this.transactions = transactions;
    this.accounts = accounts;
    this.categories = categories;
    this.extensions = extensions;
    this.drafts = drafts;
    this.chats = chats;
    this.ids = ids;
    this.json = json;
  }

  // 返回当前待办或指定草稿的状态，用于恢复会话及核对已确认的原单。
  @Transactional(readOnly = true)
  public Draft findExpenseDraft(String ledger, String actor, String chat, String id) {
    ledgers.requireReadAccess(ledger, actor);
    owned(ledger, actor, chat);
    return id == null
        ? drafts.pending(chat).orElse(null)
        : drafts.find(chat, id).orElseThrow(this::missing);
  }

  // 持有账本写权限锁后，同时保存草稿内容与用户看到的回复。
  @Transactional
  public Draft editExpenseDraft(
      String ledger,
      String actor,
      String chat,
      String id,
      Long version,
      long revision,
      String question,
      Fields values) {
    ledgers.requireWriteAccess(ledger, actor);
    var conversation = owned(ledger, actor, chat);
    if (conversation.revision() != revision) throw changed();
    var fields = validate(ledger, actor, values);
    Draft draft;
    if (id == null) {
      if (version != null || drafts.pending(chat).isPresent()) {
        throw changed();
      }
      draft = drafts.create(chat, ids.next(new IdPrefix("draft")), fields);
    } else {
      var old = drafts.find(chat, id).orElseThrow(this::missing);
      if (old.status() != Status.PENDING || version == null || old.version() != version) {
        throw changed();
      }
      draft = drafts.update(chat, id, fields);
    }
    String reply =
        fields.amountCents() == null
            ? "这笔花了多少钱？"
            : fields.categoryCode() == null ? "这笔记在哪个支出分类？" : "请核对草稿后确认记账。";
    append(conversation, question, reply, null);
    return draft;
  }

  // 确认草稿与写流水在同一事务中提交。
  @Transactional
  public Draft confirmExpenseDraft(
      String ledger, String actor, String chat, String id, long version) {
    return finishExpenseDraft(ledger, actor, chat, id, version, Status.CONFIRMED);
  }

  // 放弃草稿只更新状态和对应的会话消息。
  @Transactional
  public Draft discardExpenseDraft(
      String ledger, String actor, String chat, String id, long version) {
    return finishExpenseDraft(ledger, actor, chat, id, version, Status.DISCARDED);
  }

  // 共享归属、版本和终态校验，重复操作返回同一份草稿。
  private Draft finishExpenseDraft(
      String ledger, String actor, String chat, String id, long version, Status target) {
    ledgers.requireWriteAccess(ledger, actor);
    var conversation = owned(ledger, actor, chat);
    var draft = drafts.find(chat, id).orElseThrow(this::missing);
    boolean confirm = target == Status.CONFIRMED;
    if (draft.status() == target) return draft;
    if (draft.status() != Status.PENDING || draft.version() != version) throw changed();
    String source = null;
    if (confirm) {
      var f = validate(ledger, actor, draft.fields());
      if (f.amountCents() == null || f.categoryCode() == null) {
        throw invalid("请补全金额和支出分类");
      }
      var row =
          transactions.recordLedgerTransaction(
              new RecordLedgerTransactionCommand(
                  ledger,
                  actor,
                  TransactionDirection.EXPENSE,
                  f.amountCents(),
                  f.bookedOn(),
                  f.categoryCode(),
                  f.note(),
                  f.accountId()));
      source = row.id();
    }
    var result = drafts.finish(chat, id, target, source);
    append(conversation, confirm ? "确认记账" : "放弃草稿", confirm ? "已记一笔支出。" : "草稿已放弃。", source);
    return result;
  }

  // 日常支出只开放已启用且不要求附加业务字段的分类。
  private Fields validate(String ledger, String actor, Fields f) {
    if (f.bookedOn() == null || f.bookedOn().getYear() < 1900 || f.bookedOn().getYear() > 9999) {
      throw invalid("请选择有效日期");
    }
    if (f.amountCents() != null && (f.amountCents() < 1 || f.amountCents() > 9999999999L)) {
      throw invalid("金额应在 0.01 至 99,999,999.99 元之间");
    }
    String note = f.note() == null ? "" : f.note().strip();
    if (note.length() > 100) throw invalid("备注最多 100 个字符");
    if (f.categoryCode() != null
        && (categories.listLedgerCategories(ledger, actor).stream()
                .noneMatch(c ->
                    c.active()
                        && c.direction() == TransactionDirection.EXPENSE
                        && c.code().equals(f.categoryCode()))
            || !extensions
                .capabilities(TransactionDirection.EXPENSE, f.categoryCode())
                .isEmpty())) {
      throw invalid("请选择启用的日常支出分类");
    }
    accounts.requireAccountInLedger(ledger, f.accountId());
    return new Fields(f.bookedOn(), f.amountCents(), f.categoryCode(), f.accountId(), note);
  }

  // 历史消息中只写最终原单引用；待确认金额保存在草稿中。
  private void append(
      AssistantConversationRepository.Conversation c, String question, String text, String source) {
    var answer = new AssistantConversationApplicationService.Answer(text, null, null, source);
    if (!chats.append(
        c.id(),
        c.revision(),
        UUID.randomUUID().toString(),
        question,
        json.writeValueAsString(answer))) {
      throw changed();
    }
  }

  private AssistantConversationRepository.Conversation owned(
      String ledger, String actor, String chat) {
    return chats.find(chat, ledger, actor).orElseThrow(this::missing);
  }

  private FoundationException missing() {
    return new FoundationException(
        new ErrorCode("XIAOBU_CHAT_UNAVAILABLE"), ErrorCategory.NOT_FOUND, "对话或草稿不可用", Map.of());
  }

  private static FoundationException changed() {
    return new FoundationException(
        new ErrorCode("XIAOBU_DRAFT_CHANGED"), ErrorCategory.CONFLICT, "草稿已变更，请重新核对", Map.of());
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_DRAFT_INVALID"), message);
  }
}
