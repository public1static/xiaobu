package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.xiaobu.ledger.domain.RentalContract;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
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
@Table(name = "rental_contracts")
public class RentalContractEntity extends AuditableEntity implements Persistable<String> {
  @Id
  @Column(nullable = false, length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(name = "home_id", nullable = false, length = 48, updatable = false)
  private String homeId;

  @Column(name = "landlord_name", nullable = false, length = 60, updatable = false)
  private String landlordName;

  @Column(name = "starts_on", nullable = false, updatable = false)
  private LocalDate startsOn;

  @Column(name = "ends_on", nullable = false, updatable = false)
  private LocalDate endsOn;

  @Column(name = "monthly_rent_cents", nullable = false, updatable = false)
  private long monthlyRentCents;

  @Column(name = "deposit_cents", nullable = false, updatable = false)
  private long depositCents;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private RentalPaymentCycle cycle;

  @Column(name = "bill_id", length = 48)
  private String billId;

  @Column(nullable = false, length = 100, updatable = false)
  private String note;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Column(nullable = false)
  private boolean deleted;

  @Column(name = "remind_days", nullable = false)
  private int remindDays;

  @Column(name = "handled_end")
  private LocalDate handledEnd;

  @Column(name = "previous_contract_id", length = 48, updatable = false)
  private String previousContractId;

  @Column(name = "landlord_contact_id", length = 48, updatable = false)
  private String landlordContactId;

  @Column(name = "landlord_distinction", length = 60, updatable = false)
  private String landlordDistinction;

  @Transient private boolean newEntity = true;

  protected RentalContractEntity() {}

  RentalContractEntity(RentalContract row, String actorId) {
    id = row.id();
    ledgerId = row.ledgerId();
    homeId = row.homeId();
    landlordName = row.landlordName();
    startsOn = row.startsOn();
    endsOn = row.endsOn();
    monthlyRentCents = row.monthlyRentCents();
    depositCents = row.depositCents();
    cycle = row.cycle();
    billId = row.billId();
    note = row.note();
    remindDays = row.remindDays();
    handledEnd = row.handledEnd();
    previousContractId = row.previousContractId();
    landlordContactId = row.landlordContactId();
    landlordDistinction = row.landlordDistinction();
    createdBy = actorId;
    updatedBy = actorId;
  }

  void linkBill(String billId, String actorId) {
    this.billId = billId;
    updatedBy = actorId;
  }

  void configureExpiryReminder(int days, String actorId) {
    remindDays = days;
    updatedBy = actorId;
  }

  void completeExpiryReminder(LocalDate endsOn, String actorId) {
    handledEnd = endsOn;
    updatedBy = actorId;
  }

  RentalContract toDomain() {
    return new RentalContract(
        id,
        ledgerId,
        homeId,
        landlordName,
        startsOn,
        endsOn,
        monthlyRentCents,
        depositCents,
        cycle,
        billId,
        note,
        remindDays,
        handledEnd,
        previousContractId,
        landlordContactId,
        landlordDistinction);
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
