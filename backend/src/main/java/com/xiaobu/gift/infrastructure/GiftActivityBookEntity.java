package com.xiaobu.gift.infrastructure;

import com.xiaobu.gift.domain.GiftActivityBook;
import com.xiaobu.ledger.domain.TransactionDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "gift_books")
class GiftActivityBookEntity extends GiftDirectoryEntity {
  @Column(nullable = false, length = 60)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private TransactionDirection direction;

  @Column(name = "default_date")
  private LocalDate defaultDate;

  @Column(name = "default_reason", length = 60)
  private String defaultReason;

  @Column(nullable = false)
  private boolean archived;

  protected GiftActivityBookEntity() {}

  GiftActivityBookEntity(String ledgerId, String actorId, GiftActivityBook book) {
    super(book.id(), ledgerId, actorId);
    name = book.name();
    direction = book.direction();
    defaultDate = book.defaultDate();
    defaultReason = book.defaultReason();
    archived = book.archived();
  }

  void changeArchive(String actorId, boolean value) {
    archived = value;
    updatedBy = actorId;
  }

  GiftActivityBook toDomain() {
    return new GiftActivityBook(getId(), name, direction, defaultDate, defaultReason, archived);
  }
}
