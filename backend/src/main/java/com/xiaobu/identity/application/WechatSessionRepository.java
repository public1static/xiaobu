package com.xiaobu.identity.application;

import com.xiaobu.identity.domain.WechatSession;
import java.time.Instant;
import java.util.Optional;

public interface WechatSessionRepository {

  void add(WechatSession session);

  Optional<WechatSession> findActiveByDigest(String digest, Instant now);

  void revoke(String digest);
}
