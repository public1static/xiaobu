package com.xiaobu.assistant.infrastructure;

import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Draft;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Fields;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

@Entity
@Table(name = "assistant_expense_drafts")
class AssistantExpenseDraftEntity {
  // 服务端分配的草稿编号，历次确认指向同一份草稿。
  @Id
  @Column(length = 48)
  private String id;

  // 私人会话限定草稿归属。
  @Column(nullable = false, length = 48)
  private String conversationId;

  // 乐观锁版本用于拒绝过期确认卡。
  @Version private Long version;

  // 终态保留，供回包丢失后核对。
  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "varchar(16)")
  private Status status;

  // 以下字段为用户当前核对的账务内容，金额和分类可在追问时补充。
  private LocalDate bookedOn;
  private Long amountCents;

  @Column(length = 20)
  private String categoryCode;

  @Column(length = 48)
  private String accountId;

  @Column(length = 100)
  private String note;

  // 确认成功后保存真实流水 ID。
  @Column(length = 48)
  private String transactionId;

  protected AssistantExpenseDraftEntity() {}

  AssistantExpenseDraftEntity(String conversation, String id, Fields fields) {
    this.conversationId = conversation;
    this.id = id;
    this.status = Status.PENDING;
    apply(fields);
  }

  // 替换整张经校验的草稿，允许清除可选账户或备注。
  void apply(Fields f) {
    bookedOn = f.bookedOn();
    amountCents = f.amountCents();
    categoryCode = f.categoryCode();
    accountId = f.accountId();
    note = f.note();
  }

  // 完成和放弃共享终态转换，流水编号仅用于完成状态。
  void finish(Status next, String source) {
    status = next;
    transactionId = source;
  }

  // 返回已刷新的数据库版本，供下一次确认使用。
  Draft snapshot() {
    return new Draft(
        id,
        version,
        status,
        new Fields(bookedOn, amountCents, categoryCode, accountId, note),
        transactionId);
  }
}
