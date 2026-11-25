package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.TransactionDirection;
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
@Table(name = "ledger_categories")
public class LedgerCategoryEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", length = 48, updatable = false, nullable = false)
  private String ledgerId;

  @Column(length = 20, updatable = false, nullable = false)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(length = 8, updatable = false, nullable = false)
  private TransactionDirection direction;

  @Column(length = 24, updatable = false, nullable = false)
  private String name;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean active;

  @Transient private boolean newEntity = true;

  protected LedgerCategoryEntity() {}

  public LedgerCategoryEntity(
      String id,
      String ledgerId,
      String code,
      TransactionDirection direction,
      String name,
      int displayOrder,
      boolean active) {
    this.id = id;
    this.ledgerId = ledgerId;
    this.code = code;
    this.direction = direction;
    this.name = name;
    this.displayOrder = displayOrder;
    this.active = active;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public String getCode() {
    return code;
  }

  public TransactionDirection getDirection() {
    return direction;
  }

  public String getName() {
    return name;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public boolean isActive() {
    return active;
  }

  void configure(int order, boolean nextActive) {
    displayOrder = order;
    active = nextActive;
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
