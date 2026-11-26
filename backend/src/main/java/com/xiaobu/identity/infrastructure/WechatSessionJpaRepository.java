package com.xiaobu.identity.infrastructure;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface WechatSessionJpaRepository extends Repository<WechatSessionEntity, String> {

  <S extends WechatSessionEntity> S save(S entity);

  Optional<WechatSessionEntity> findByTokenDigestAndExpiresAtAfter(String tokenDigest, Instant now);

  @Modifying
  @Transactional
  @Query(
      "update WechatSessionEntity s set s.expiresAt = :now where s.tokenDigest = :digest and s.expiresAt > :now")
  void expire(@Param("digest") String tokenDigest, @Param("now") Instant now);
}
