package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.xiaobu.ledger.domain.FixedBillPayment;
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
@Table(name = "fixed_bill_payments")
public class FixedBillPaymentEntity extends AuditableEntity implements Persistable<String> {
  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "bill_id", nullable = false, length = 48, updatable = false)
  private String billId;

  @Column(name = "period_start", nullable = false, updatable = false)
  private LocalDate period;

  @Column(name = "transaction_id", nullable = false, length = 48, updatable = false)
  private String transactionId;

  @Column(name = "paid_on", nullable = false, updatable = false)
  private LocalDate paidOn;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Transient private boolean newEntity = true;

  protected FixedBillPaymentEntity() {}

  FixedBillPaymentEntity(FixedBillPayment row, String actorId) {
    id = row.id();
    billId = row.billId();
    period = row.period();
    transactionId = row.transactionId();
    paidOn = row.paidOn();
    amountCents = row.amountCents();
    createdBy = actorId;
    updatedBy = actorId;
  }

  FixedBillPayment toDomain() {
    return new FixedBillPayment(id, billId, period, transactionId, paidOn, amountCents);
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
