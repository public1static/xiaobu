package com.xiaobu.identity.domain;

import java.time.Instant;

public record WechatSession(
    String tokenDigest, String actorId, String displayName, String ledgerId, Instant expiresAt) {}
