package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.xiaobu.ledger.domain.RentalDeposit;
import com.xiaobu.ledger.domain.RentalDepositKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.LocalDate;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "rental_deposits")
public class RentalDepositEntity extends AuditableEntity implements Persistable<String> {
  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(name = "contract_id", nullable = false, length = 48, updatable = false)
  private String contractId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12, updatable = false)
  private RentalDepositKind kind;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(name = "booked_on", nullable = false)
  private LocalDate bookedOn;

  @Column(name = "account_id", length = 48)
  private String accountId;

  @Column(nullable = false, length = 100)
  private String note;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Column(nullable = false)
  private boolean deleted;

  @Transient private boolean newEntity = true;

  protected RentalDepositEntity() {}

  RentalDepositEntity(RentalDeposit row) {
    id = row.id();
    ledgerId = row.ledgerId();
    contractId = row.contractId();
    kind = row.kind();
    createdBy = row.createdBy();
    revise(row, row.createdBy());
  }

  RentalDeposit toDomain() {
    return new RentalDeposit(
        id, ledgerId, contractId, kind, amountCents, bookedOn, accountId, note, createdBy, version);
  }

  void revise(RentalDeposit row, String actorId) {
    amountCents = row.amountCents();
    bookedOn = row.bookedOn();
    accountId = row.accountId();
    note = row.note();
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
