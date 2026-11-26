package com.xiaobu.identity.infrastructure;

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
@Table(name = "wechat_preview_sessions")
public class WechatSessionEntity implements Persistable<String> {

  @Id
  @Column(name = "token_digest", length = 64, columnDefinition = "CHAR(64)", updatable = false)
  private String tokenDigest;

  @Column(name = "actor_id", length = 48, nullable = false, updatable = false)
  private String actorId;

  @Column(name = "display_name", length = 32, nullable = false, updatable = false)
  private String displayName;

  @Column(name = "ledger_id", length = 48, nullable = false, updatable = false)
  private String ledgerId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Transient private boolean newEntity = true;

  protected WechatSessionEntity() {}

  public WechatSessionEntity(
      String tokenDigest, String actorId, String displayName, String ledgerId, Instant expiresAt) {
    this.tokenDigest = tokenDigest;
    this.actorId = actorId;
    this.displayName = displayName;
    this.ledgerId = ledgerId;
    this.expiresAt = expiresAt;
  }

  @Override
  public String getId() {
    return tokenDigest;
  }

  public String getTokenDigest() {
    return tokenDigest;
  }

  public String getActorId() {
    return actorId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
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
