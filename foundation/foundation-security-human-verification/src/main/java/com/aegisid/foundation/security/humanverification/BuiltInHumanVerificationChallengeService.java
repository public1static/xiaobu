package com.aegisid.foundation.security.humanverification;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BuiltInHumanVerificationChallengeService implements HumanVerificationProvider {
  private static final String PROVIDER = "BUILT_IN_IMAGE";
  private static final String SESSION_ID_ATTRIBUTE = "humanVerificationSessionId";
  private static final int TTL_SECONDS = 300;
  private static final int MAX_ATTEMPTS = 3;
  private static final char[] ANSWER_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

  private final Clock clock;
  private final Supplier<String> answerGenerator;
  private final boolean debugAnswerEnabled;
  private final Map<String, StoredChallenge> challenges = new ConcurrentHashMap<>();

  public BuiltInHumanVerificationChallengeService() {
    this(false);
  }

  @Autowired
  public BuiltInHumanVerificationChallengeService(
      @Value("${aegisid.human-verification.debug-answer-enabled:false}")
          boolean debugAnswerEnabled) {
    this(
        Clock.systemUTC(),
        BuiltInHumanVerificationChallengeService::randomAnswer,
        debugAnswerEnabled);
  }

  public BuiltInHumanVerificationChallengeService(Clock clock, Supplier<String> answerGenerator) {
    this(clock, answerGenerator, false);
  }

  public BuiltInHumanVerificationChallengeService(
      Clock clock, Supplier<String> answerGenerator, boolean debugAnswerEnabled) {
    this.clock = clock;
    this.answerGenerator = answerGenerator;
    this.debugAnswerEnabled = debugAnswerEnabled;
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  public HumanVerificationChallengeView create(
      HumanVerificationApplication application,
      HumanVerificationScene scene,
      HumanVerificationRequestContext context) {
    return create(application, scene, null, null, context);
  }

  public HumanVerificationChallengeView create(
      HumanVerificationApplication application,
      HumanVerificationScene scene,
      String target,
      String method,
      HumanVerificationRequestContext context) {
    Instant now = clock.instant();
    String id = "cap_" + UUID.randomUUID().toString().replace("-", "");
    String answer = answerGenerator.get();
    String sessionBinding = sessionBinding(context);
    StoredChallenge challenge =
        new StoredChallenge(
            id,
            application.tenantId(),
            application.userPoolId(),
            application.applicationId(),
            scene.canonical(),
            targetHash(target),
            normalizeMethod(method),
            hash(answer.toUpperCase(Locale.ROOT)),
            hash(sessionBinding),
            renderAnswerImage(answer),
            0,
            now,
            now.plusSeconds(TTL_SECONDS),
            false);
    invalidatePreviousChallenges(challenge);
    challenges.put(id, challenge);
    return new HumanVerificationChallengeView(
        id,
        PROVIDER,
        "/api/v1/login/human-verification/challenges/" + id + "/image",
        challenge.expiresAt(),
        debugAnswerEnabled ? answer : null);
  }

  public byte[] image(String challengeId) {
    StoredChallenge challenge = challenges.get(challengeId);
    if (challenge == null || challenge.used() || challenge.expiresAt().isBefore(clock.instant())) {
      return null;
    }
    return challenge.imagePng().clone();
  }

  @Override
  public boolean verify(HumanVerificationRequest request) {
    if (request.challengeId() == null
        || request.challengeId().isBlank()
        || request.answer() == null
        || request.answer().isBlank()) {
      return false;
    }
    AtomicBoolean verified = new AtomicBoolean(false);
    challenges.computeIfPresent(
        request.challengeId(),
        (id, current) -> {
          if (current.used()
              || current.expiresAt().isBefore(clock.instant())
              || !current.matchesContext(request)) {
            return current;
          }
          StoredChallenge attempted = current.withAttempt();
          boolean valid =
              MessageDigest.isEqual(
                  current.answerHash().getBytes(StandardCharsets.UTF_8),
                  hash(request.answer().toUpperCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
          verified.set(valid);
          if (valid || attempted.attemptCount() >= MAX_ATTEMPTS) {
            return attempted.asUsed();
          }
          return attempted;
        });
    return verified.get();
  }

  private static String randomAnswer() {
    SecureRandom random = new SecureRandom();
    StringBuilder answer = new StringBuilder(5);
    for (int i = 0; i < 5; i++) {
      answer.append(ANSWER_CHARS[random.nextInt(ANSWER_CHARS.length)]);
    }
    return answer.toString();
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required", ex);
    }
  }

  private void invalidatePreviousChallenges(StoredChallenge replacement) {
    challenges.forEach(
        (id, existing) -> {
          if (!existing.used() && existing.sameBindingAs(replacement)) {
            challenges.replace(id, existing, existing.asUsed());
          }
        });
  }

  private static String targetHash(String target) {
    String normalized = normalizeTarget(target);
    return normalized == null ? null : hash(normalized);
  }

  private static String normalizeTarget(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeMethod(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static byte[] renderAnswerImage(String answer) {
    try {
      BufferedImage image = new BufferedImage(160, 56, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = image.createGraphics();
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setColor(new Color(246, 248, 252));
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      graphics.setColor(new Color(71, 85, 105));
      graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
      graphics.drawString(answer, 28, 38);
      graphics.setColor(new Color(148, 163, 184));
      graphics.drawLine(10, 18, 150, 42);
      graphics.drawLine(12, 44, 146, 14);
      graphics.dispose();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ImageIO.write(image, "png", output);
      return output.toByteArray();
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("human verification image rendering failed", ex);
    }
  }

  private static String sessionBinding(HumanVerificationRequestContext context) {
    Object existing = context.sessionAttribute(SESSION_ID_ATTRIBUTE);
    if (existing instanceof String value && !value.isBlank()) {
      return value;
    }
    String created = UUID.randomUUID().toString();
    context.setSessionAttribute(SESSION_ID_ATTRIBUTE, created);
    return created;
  }

  private record StoredChallenge(
      String id,
      String tenantId,
      String userPoolId,
      String applicationId,
      HumanVerificationScene scene,
      String targetHash,
      String method,
      String answerHash,
      String sessionBindingHash,
      byte[] imagePng,
      int attemptCount,
      Instant createdAt,
      Instant expiresAt,
      boolean used) {

    boolean matchesContext(HumanVerificationRequest request) {
      return tenantId.equals(request.application().tenantId())
          && userPoolId.equals(request.application().userPoolId())
          && applicationId.equals(request.application().applicationId())
          && scene == request.scene().canonical()
          && java.util.Objects.equals(
              targetHash, BuiltInHumanVerificationChallengeService.targetHash(request.target()))
          && java.util.Objects.equals(method, normalizeMethod(request.method()))
          && sessionBindingHash.equals(hash(sessionBinding(request.context())));
    }

    boolean sameBindingAs(StoredChallenge replacement) {
      return tenantId.equals(replacement.tenantId())
          && userPoolId.equals(replacement.userPoolId())
          && applicationId.equals(replacement.applicationId())
          && scene == replacement.scene()
          && java.util.Objects.equals(targetHash, replacement.targetHash())
          && java.util.Objects.equals(method, replacement.method())
          && sessionBindingHash.equals(replacement.sessionBindingHash());
    }

    StoredChallenge withAttempt() {
      return new StoredChallenge(
          id,
          tenantId,
          userPoolId,
          applicationId,
          scene,
          targetHash,
          method,
          answerHash,
          sessionBindingHash,
          imagePng,
          attemptCount + 1,
          createdAt,
          expiresAt,
          used);
    }

    StoredChallenge asUsed() {
      return new StoredChallenge(
          id,
          tenantId,
          userPoolId,
          applicationId,
          scene,
          targetHash,
          method,
          answerHash,
          sessionBindingHash,
          imagePng,
          attemptCount,
          createdAt,
          expiresAt,
          true);
    }
  }
}
