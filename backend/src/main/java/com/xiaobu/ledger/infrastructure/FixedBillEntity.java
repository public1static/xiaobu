package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.xiaobu.ledger.domain.FixedBill;
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
@Table(name = "fixed_bills")
public class FixedBillEntity extends AuditableEntity implements Persistable<String> {
  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(name = "name", nullable = false, length = 40, updatable = false)
  private String name;

  @Column(name = "first_due_on", nullable = false, updatable = false)
  private LocalDate firstDueOn;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Column(name = "category_code", nullable = false, length = 48, updatable = false)
  private String categoryCode;

  @Column(name = "account_id", length = 48, updatable = false)
  private String accountId;

  @Column(nullable = false)
  private boolean deleted;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Column(name = "stop_month")
  private LocalDate stopMonth;

  @Transient private boolean newEntity = true;

  protected FixedBillEntity() {}

  FixedBillEntity(FixedBill row, String actorId) {
    id = row.id();
    ledgerId = row.ledgerId();
    name = row.name();
    firstDueOn = row.firstDueOn();
    amountCents = row.amountCents();
    categoryCode = row.categoryCode();
    accountId = row.accountId();
    stopMonth = row.stopMonth();
    createdBy = actorId;
    updatedBy = actorId;
  }

  FixedBill toDomain() {
    return new FixedBill(
        id, ledgerId, name, firstDueOn, amountCents, categoryCode, accountId, stopMonth);
  }

  void closePlan(LocalDate stopMonth, String actorId) {
    this.stopMonth = stopMonth;
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
