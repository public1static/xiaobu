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
@Table(name = "ledger_accounts")
public class LedgerAccountEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(nullable = false, length = 32)
  private String name;

  @Column(name = "opening_balance_cents", nullable = false, updatable = false)
  private long openingBalanceCents;

  @Transient private boolean newEntity = true;

  protected LedgerAccountEntity() {}

  public LedgerAccountEntity(String id, String ledgerId, String name, long openingBalanceCents) {
    this.id = id;
    this.ledgerId = ledgerId;
    this.name = name;
    this.openingBalanceCents = openingBalanceCents;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public String getName() {
    return name;
  }

  public long getOpeningBalanceCents() {
    return openingBalanceCents;
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
