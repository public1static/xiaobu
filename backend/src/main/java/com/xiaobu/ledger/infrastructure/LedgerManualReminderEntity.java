package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.xiaobu.ledger.domain.LedgerManualReminder;
import com.xiaobu.ledger.domain.ManualReminderFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "ledger_manual_reminders")
public class LedgerManualReminderEntity extends AuditableEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(nullable = false, length = 60)
  private String title;

  @Column(name = "due_on", nullable = false)
  private LocalDate dueOn;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "remind_at", nullable = false)
  private LocalDateTime remindAt;

  @Column(nullable = false)
  private boolean completed;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 12)
  private ManualReminderFrequency frequency;

  @Column(name = "anchor_on", nullable = false)
  private LocalDate anchorOn;

  @Column(nullable = false)
  private long revision;

  @Column(nullable = false)
  private boolean stopped;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Column(nullable = false)
  private boolean deleted;

  @Transient private boolean newEntity = true;

  protected LedgerManualReminderEntity() {}

  LedgerManualReminderEntity(
      String id, String ledgerId, String actorId, LedgerManualReminder reminder) {
    this.id = id;
    this.ledgerId = ledgerId;
    createdBy = actorId;
    revise(reminder, actorId);
  }

  void revise(LedgerManualReminder reminder, String actorId) {
    title = reminder.title();
    dueOn = reminder.dueOn();
    remindAt = reminder.remindAt();
    completed = reminder.completed();
    frequency = reminder.frequency();
    anchorOn = reminder.anchorOn();
    revision = reminder.revision();
    stopped = reminder.stopped();
    updatedBy = actorId;
  }

  LedgerManualReminder toDomain() {
    return new LedgerManualReminder(
        id, title, dueOn, remindAt, completed, frequency, anchorOn, revision, stopped);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  private void markPersisted() {
    newEntity = false;
  }
}
