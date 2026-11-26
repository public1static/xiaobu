package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.xiaobu.ledger.domain.RentalHome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "rental_homes")
public class RentalHomeEntity extends AuditableEntity implements Persistable<String> {
  @Id
  @Column(nullable = false, length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(nullable = false, length = 40)
  private String name;

  @Column(nullable = false, length = 120)
  private String address;

  @Column(nullable = false, length = 100)
  private String note;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Column(nullable = false)
  private boolean deleted;

  @Transient private boolean newEntity = true;

  protected RentalHomeEntity() {}

  RentalHomeEntity(RentalHome row, String actorId) {
    id = row.id();
    ledgerId = row.ledgerId();
    name = row.name();
    address = row.address();
    note = row.note();
    createdBy = actorId;
    updatedBy = actorId;
  }

  void updateHome(RentalHome row, String actorId) {
    name = row.name();
    address = row.address();
    note = row.note();
    updatedBy = actorId;
  }

  RentalHome toDomain() {
    return new RentalHome(id, ledgerId, name, address, note);
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
