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
@Table(name = "ledger_members")
public class LedgerMembershipEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", length = 48, nullable = false, updatable = false)
  private String ledgerId;

  @Column(name = "member_id", length = 48, nullable = false, updatable = false)
  private String memberId;

  @Column(name = "inviter_name", length = 32, nullable = false, updatable = false)
  private String inviterName;

  @Transient private boolean newEntity = true;

  protected LedgerMembershipEntity() {}

  public LedgerMembershipEntity(String id, String ledgerId, String memberId, String inviterName) {
    this.id = id;
    this.ledgerId = ledgerId;
    this.memberId = memberId;
    this.inviterName = inviterName;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getInviterName() {
    return inviterName;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    newEntity = false;
  }
}
