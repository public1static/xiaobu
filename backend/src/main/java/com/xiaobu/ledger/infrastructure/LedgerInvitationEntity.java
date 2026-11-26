package com.xiaobu.ledger.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "ledger_invitations")
public class LedgerInvitationEntity implements Persistable<String> {

  @Id
  @Column(name = "token_digest", length = 64, columnDefinition = "CHAR(64)", updatable = false)
  private String tokenDigest;

  @Column(name = "ledger_id", length = 48, nullable = false, updatable = false)
  private String ledgerId;

  @Column(name = "inviter_id", length = 48, nullable = false, updatable = false)
  private String inviterId;

  @Column(name = "inviter_name", length = 32, nullable = false, updatable = false)
  private String inviterName;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "accepted_by", length = 48)
  private String acceptedBy;

  @Transient private boolean newEntity = true;

  protected LedgerInvitationEntity() {}

  public LedgerInvitationEntity(
      String tokenDigest,
      String ledgerId,
      String inviterId,
      String inviterName,
      Instant expiresAt,
      String acceptedBy) {
    this.tokenDigest = tokenDigest;
    this.ledgerId = ledgerId;
    this.inviterId = inviterId;
    this.inviterName = inviterName;
    this.expiresAt = expiresAt;
    this.acceptedBy = acceptedBy;
  }

  @Override
  public String getId() {
    return tokenDigest;
  }

  public String getTokenDigest() {
    return tokenDigest;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public String getInviterId() {
    return inviterId;
  }

  public String getInviterName() {
    return inviterName;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public String getAcceptedBy() {
    return acceptedBy;
  }

  void accept(String actorId) {
    acceptedBy = actorId;
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
