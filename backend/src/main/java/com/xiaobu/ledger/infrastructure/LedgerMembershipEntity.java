package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.LedgerMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
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

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private LedgerMemberRole role;

  @Column(name = "removed_at")
  private Instant removedAt;

  @Transient private boolean newEntity = true;

  protected LedgerMembershipEntity() {}

  public LedgerMembershipEntity(
      String id,
      String ledgerId,
      String memberId,
      String inviterName,
      LedgerMemberRole role,
      Instant removedAt) {
    this.id = id;
    this.ledgerId = ledgerId;
    this.memberId = memberId;
    this.inviterName = inviterName;
    this.role = role;
    this.removedAt = removedAt;
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

  public LedgerMemberRole getRole() {
    return role;
  }

  public Instant getRemovedAt() {
    return removedAt;
  }

  void changeRole(LedgerMemberRole newRole) {
    role = newRole;
  }

  void remove(Instant when) {
    removedAt = when;
  }

  void restoreReadOnly() {
    role = LedgerMemberRole.READ_ONLY;
    removedAt = null;
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
