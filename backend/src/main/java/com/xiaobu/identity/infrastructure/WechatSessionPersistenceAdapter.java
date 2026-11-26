package com.xiaobu.identity.infrastructure;

import com.xiaobu.identity.application.WechatSessionRepository;
import com.xiaobu.identity.domain.WechatSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class WechatSessionPersistenceAdapter implements WechatSessionRepository {

  private final WechatSessionJpaRepository sessions;
  private final WechatSessionPersistenceMapper mapper;

  WechatSessionPersistenceAdapter(
      WechatSessionJpaRepository sessions, WechatSessionPersistenceMapper mapper) {
    this.sessions = sessions;
    this.mapper = mapper;
  }

  @Override
  public void add(WechatSession session) {
    sessions.save(mapper.toNewEntity(session));
  }

  @Override
  public Optional<WechatSession> findActiveByDigest(String digest, Instant now) {
    return sessions.findByTokenDigestAndExpiresAtAfter(digest, now).map(mapper::toDomain);
  }

  @Override
  public void revoke(String digest) {
    sessions.expire(digest, Instant.now());
  }
}
