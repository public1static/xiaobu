package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
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
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransactionEntity extends AuditableEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48)
  private String ledgerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private TransactionDirection direction;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(name = "booked_on", nullable = false)
  private LocalDate bookedOn;

  @Column(name = "category_code", nullable = false, length = 20)
  private String categoryCode;

  @Column(nullable = false, length = 100)
  private String note;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Column(nullable = false)
  private boolean deleted;

  // The application assigns the ID before the first save.
  @Transient private boolean newEntity = true;

  protected LedgerTransactionEntity() {}

  public LedgerTransactionEntity(
      String id,
      String ledgerId,
      TransactionDirection direction,
      long amountCents,
      LocalDate bookedOn,
      String categoryCode,
      String note,
      String createdBy,
      String updatedBy,
      boolean deleted) {
    this.id = id;
    this.ledgerId = ledgerId;
    this.direction = direction;
    this.amountCents = amountCents;
    this.bookedOn = bookedOn;
    this.categoryCode = categoryCode;
    this.note = note;
    this.createdBy = createdBy;
    this.updatedBy = updatedBy;
    this.deleted = deleted;
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
  void markNotNew() {
    newEntity = false;
  }

  public long getAmountCents() {
    return amountCents;
  }

  public LocalDate getBookedOn() {
    return bookedOn;
  }

  public String getCategoryCode() {
    return categoryCode;
  }

  public String getNote() {
    return note;
  }
}
