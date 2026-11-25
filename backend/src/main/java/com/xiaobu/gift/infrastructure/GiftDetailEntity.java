package com.xiaobu.gift.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.xiaobu.gift.domain.GiftBook;
import com.xiaobu.gift.domain.GiftDetail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "ledger_gift_details")
class GiftDetailEntity extends AuditableEntity implements Persistable<String> {
  @Id
  @Column(name = "transaction_id", length = 48, updatable = false)
  private String transactionId;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "book_code", nullable = false, length = 24)
  private GiftBook book;

  @Column(name = "person_name", length = 60)
  private String personName;

  @Column(length = 60)
  private String reason;

  @Column(nullable = false)
  private boolean deleted;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Transient private boolean newEntity = true;

  protected GiftDetailEntity() {}

  GiftDetailEntity(String ledgerId, String transactionId, String actorId, GiftDetail detail) {
    this.ledgerId = ledgerId;
    this.transactionId = transactionId;
    createdBy = actorId;
    revise(actorId, detail);
  }

  void revise(String actorId, GiftDetail detail) {
    book = detail.book();
    personName = detail.personName();
    reason = detail.reason();
    deleted = false;
    updatedBy = actorId;
  }

  void remove(String actorId) {
    deleted = true;
    updatedBy = actorId;
  }

  GiftDetail toDomain() {
    return new GiftDetail(book, personName, reason);
  }

  @Override
  public String getId() {
    return transactionId;
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
