package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "ledger_refunds")
public class LedgerRefundEntity extends AuditableEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(name = "transaction_id", nullable = false, length = 48, updatable = false)
  private String transactionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", insertable = false, updatable = false)
  private LedgerTransactionEntity original;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Column(name = "received_on", nullable = false, updatable = false)
  private LocalDate receivedOn;

  @Column(name = "account_id", length = 48, updatable = false)
  private String accountId;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String actorId;

  @Column(name = "updated_by", nullable = false, length = 48, updatable = false)
  private String updatedBy;

  @Transient private boolean newEntity = true;

  protected LedgerRefundEntity() {}

  public LedgerRefundEntity(
      String id,
      String ledgerId,
      String transactionId,
      long amountCents,
      LocalDate receivedOn,
      String accountId,
      String actorId) {
    this.id = id;
    this.ledgerId = ledgerId;
    this.transactionId = transactionId;
    this.amountCents = amountCents;
    this.receivedOn = receivedOn;
    this.accountId = accountId;
    this.actorId = actorId;
    this.updatedBy = actorId;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public long getAmountCents() {
    return amountCents;
  }

  public LocalDate getReceivedOn() {
    return receivedOn;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getActorId() {
    return actorId;
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
