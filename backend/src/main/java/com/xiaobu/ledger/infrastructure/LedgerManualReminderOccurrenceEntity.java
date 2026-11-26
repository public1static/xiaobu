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
@Table(name = "ledger_manual_reminder_occurrences")
public class LedgerManualReminderOccurrenceEntity extends AuditableEntity
    implements Persistable<String> {

  @Id
  @Column(length = 72, updatable = false)
  private String id;

  @Column(name = "reminder_id", nullable = false, length = 48, updatable = false)
  private String reminderId;

  @Column(nullable = false, updatable = false)
  private long revision;

  @Column(nullable = false, length = 60, updatable = false)
  private String title;

  @Column(name = "due_on", nullable = false, updatable = false)
  private LocalDate dueOn;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "remind_at", nullable = false, updatable = false)
  private LocalDateTime remindAt;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 12, updatable = false)
  private ManualReminderFrequency frequency;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Column(nullable = false)
  private boolean deleted;

  @Transient private boolean newEntity = true;

  protected LedgerManualReminderOccurrenceEntity() {}

  LedgerManualReminderOccurrenceEntity(LedgerManualReminder reminder, String actorId) {
    id = reminder.id() + ":" + reminder.revision();
    reminderId = reminder.id();
    revision = reminder.revision();
    title = reminder.title();
    dueOn = reminder.dueOn();
    remindAt = reminder.remindAt();
    frequency = reminder.frequency();
    createdBy = actorId;
    updatedBy = actorId;
  }

  LedgerManualReminder toDomain() {
    return new LedgerManualReminder(
        reminderId, title, dueOn, remindAt, true, frequency, dueOn, revision, false);
  }

  void revert(String actorId) {
    deleted = true;
    updatedBy = actorId;
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
