package com.xiaobu.identity.application;

import com.xiaobu.identity.domain.WechatSession;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("wechat-preview")
public class WechatSessionApplicationService {

  private static final Duration SESSION_LIFETIME = Duration.ofHours(12);
  private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final WechatIdentityGateway identities;
  private final WechatSessionRepository sessions;
  private final PersonalLedgerApplicationService ledgers;

  WechatSessionApplicationService(
      WechatIdentityGateway identities,
      WechatSessionRepository sessions,
      PersonalLedgerApplicationService ledgers) {
    this.identities = identities;
    this.sessions = sessions;
    this.ledgers = ledgers;
  }

  public OpenWechatSessionResult openWechatSession(String loginCode) {
    String actorId = identities.resolveActorId(loginCode);
    String displayName = displayNameForActor(actorId);
    var ledger = ledgers.openPersonalLedger(actorId);
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    sessions.add(
        new WechatSession(
            digest(token),
            actorId,
            displayName,
            ledger.id(),
            Instant.now().plus(SESSION_LIFETIME)));
    return new OpenWechatSessionResult(token, displayName, ledger.id());
  }

  public Optional<WechatSession> findByAuthorization(String authorization) {
    return bearerToken(authorization)
        .flatMap(token -> sessions.findActiveByDigest(digest(token), Instant.now()));
  }

  public void closeWechatSession(String authorization) {
    bearerToken(authorization).ifPresent(token -> sessions.revoke(digest(token)));
  }

  public static String displayNameForActor(String actorId) {
    return actorId.startsWith("wx_") && actorId.length() >= 10
        ? "微信成员 " + actorId.substring(actorId.length() - 5)
        : "账本成员";
  }

  private static Optional<String> bearerToken(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) return Optional.empty();
    String token = authorization.substring(7);
    return TOKEN_FORMAT.matcher(token).matches() ? Optional.of(token) : Optional.empty();
  }

  private static String digest(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
