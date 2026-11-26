package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "ledger_account_transfers")
public class LedgerAccountTransferEntity extends AuditableEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(name = "source_account_id", nullable = false, length = 48, updatable = false)
  private String sourceAccountId;

  @Column(name = "target_account_id", nullable = false, length = 48, updatable = false)
  private String targetAccountId;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Column(name = "booked_on", nullable = false, updatable = false)
  private LocalDate bookedOn;

  @Column(nullable = false, length = 100, updatable = false)
  private String note;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Transient private boolean newEntity = true;

  protected LedgerAccountTransferEntity() {}

  public LedgerAccountTransferEntity(
      String id,
      String ledgerId,
      String sourceAccountId,
      String targetAccountId,
      long amountCents,
      LocalDate bookedOn,
      String note,
      String createdBy) {
    this.id = id;
    this.ledgerId = ledgerId;
    this.sourceAccountId = sourceAccountId;
    this.targetAccountId = targetAccountId;
    this.amountCents = amountCents;
    this.bookedOn = bookedOn;
    this.note = note;
    this.createdBy = createdBy;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public String getSourceAccountId() {
    return sourceAccountId;
  }

  public String getTargetAccountId() {
    return targetAccountId;
  }

  public long getAmountCents() {
    return amountCents;
  }

  public LocalDate getBookedOn() {
    return bookedOn;
  }

  public String getNote() {
    return note;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    newEntity = false;
  }
}
