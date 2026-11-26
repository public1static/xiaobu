package com.xiaobu.ledger.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "personal_ledgers")
public class PersonalLedgerEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "owner_id", nullable = false, length = 48, updatable = false)
  private String ownerId;

  @Transient private boolean newEntity = true;

  protected PersonalLedgerEntity() {}

  public PersonalLedgerEntity(String id, String ownerId) {
    this.id = id;
    this.ownerId = ownerId;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getOwnerId() {
    return ownerId;
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
