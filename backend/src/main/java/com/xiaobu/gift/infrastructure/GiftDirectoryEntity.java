package com.xiaobu.gift.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@MappedSuperclass
abstract class GiftDirectoryEntity extends AuditableEntity implements Persistable<String> {
  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  protected String updatedBy;

  @Transient private boolean newEntity = true;

  protected GiftDirectoryEntity() {}

  protected GiftDirectoryEntity(String id, String ledgerId, String actorId) {
    this.id = id;
    this.ledgerId = ledgerId;
    createdBy = actorId;
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
  void markNotNew() {
    newEntity = false;
  }
}
