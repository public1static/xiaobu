package com.aegisid.foundation.data.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

/** Base class for JPA entities that support timestamp-based soft deletion. */
@MappedSuperclass
public abstract class SoftDeletableEntity extends AuditableEntity {

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public Instant getDeletedAt() {
    return deletedAt;
  }

  protected void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
