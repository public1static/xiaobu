package com.aegisid.foundation.security.humanverification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuiltInHumanVerificationChallengeServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-07T10:00:00Z");

  @Test
  void createsSingleUseChallengeBoundToSceneApplicationSessionTargetAndMethod() {
    BuiltInHumanVerificationChallengeService service =
        new BuiltInHumanVerificationChallengeService(
            Clock.fixed(NOW, ZoneOffset.UTC), () -> "7KQ3X");
    HumanVerificationRequestContext browser = context("browser-a");
    HumanVerificationChallengeView challenge =
        service.create(
            application(),
            HumanVerificationScene.OTP_DISPATCH,
            "owner@example.com",
            "EMAIL_CODE",
            browser);

    assertThat(challenge.provider()).isEqualTo("BUILT_IN_IMAGE");
    assertThat(challenge.imageUrl())
        .matches("/api/v1/login/human-verification/challenges/cap_[a-f0-9]+/image");
    assertThat(challenge.debugAnswer()).isNull();
    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.OTP_DISPATCH,
                    "owner@example.com",
                    "SMS_CODE",
                    challenge.challengeId(),
                    "7KQ3X",
                    browser)))
        .isFalse();
    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.OTP_DISPATCH,
                    "owner@example.com",
                    "EMAIL_CODE",
                    challenge.challengeId(),
                    "7kq3x",
                    browser)))
        .isTrue();
    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.OTP_DISPATCH,
                    "owner@example.com",
                    "EMAIL_CODE",
                    challenge.challengeId(),
                    "7kq3x",
                    browser)))
        .isFalse();
  }

  @Test
  void exposesBuiltInImageCaptchaThroughProviderExtensionPoint() {
    HumanVerificationProvider provider =
        new BuiltInHumanVerificationChallengeService(
            Clock.fixed(NOW, ZoneOffset.UTC), () -> "7KQ3X");
    HumanVerificationRequestContext browser = context("browser-a");
    HumanVerificationChallengeView challenge =
        provider.create(
            application(),
            HumanVerificationScene.PASSWORD_RESET,
            "owner@example.com",
            "EMAIL_CODE",
            browser);

    assertThat(provider.provider()).isEqualTo("BUILT_IN_IMAGE");
    assertThat(provider.image(challenge.challengeId())).isNotEmpty();
    assertThat(
            provider.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.PASSWORD_RESET,
                    "owner@example.com",
                    "EMAIL_CODE",
                    challenge.challengeId(),
                    "7KQ3X",
                    browser)))
        .isTrue();
  }

  @Test
  void exposesDebugAnswerOnlyWhenEnabled() {
    BuiltInHumanVerificationChallengeService service =
        new BuiltInHumanVerificationChallengeService(
            Clock.fixed(NOW, ZoneOffset.UTC), () -> "7KQ3X", true);

    HumanVerificationChallengeView challenge =
        service.create(application(), HumanVerificationScene.LOGIN_SUBMIT, context("browser-a"));

    assertThat(challenge.debugAnswer()).isEqualTo("7KQ3X");
    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.LOGIN_SUBMIT,
                    challenge.challengeId(),
                    challenge.debugAnswer(),
                    context("browser-a"))))
        .isTrue();
  }

  @Test
  void rejectsDifferentSceneOrSession() {
    BuiltInHumanVerificationChallengeService service =
        new BuiltInHumanVerificationChallengeService(
            Clock.fixed(NOW, ZoneOffset.UTC), () -> "7KQ3X");
    HumanVerificationChallengeView challenge =
        service.create(application(), HumanVerificationScene.SIGN_UP, context("browser-a"));

    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.SIGN_IN,
                    challenge.challengeId(),
                    "7KQ3X",
                    context("browser-a"))))
        .isFalse();
    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.SIGN_UP,
                    challenge.challengeId(),
                    "7KQ3X",
                    context("browser-b"))))
        .isFalse();
  }

  @Test
  void acceptsDesignSubmitSceneAliasesForExistingSignInAndSignUpFlows() {
    BuiltInHumanVerificationChallengeService service =
        new BuiltInHumanVerificationChallengeService(
            Clock.fixed(NOW, ZoneOffset.UTC), () -> "7KQ3X");
    HumanVerificationRequestContext browser = context("browser-a");
    HumanVerificationChallengeView signInChallenge =
        service.create(application(), HumanVerificationScene.LOGIN_SUBMIT, browser);
    HumanVerificationChallengeView signUpChallenge =
        service.create(application(), HumanVerificationScene.REGISTER_SUBMIT, browser);

    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.SIGN_IN,
                    signInChallenge.challengeId(),
                    "7KQ3X",
                    browser)))
        .isTrue();
    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.SIGN_UP,
                    signUpChallenge.challengeId(),
                    "7KQ3X",
                    browser)))
        .isTrue();
  }

  @Test
  void thirdAttemptCanSucceedBeforeChallengeReachesMaxFailedAttempts() {
    BuiltInHumanVerificationChallengeService service =
        new BuiltInHumanVerificationChallengeService(
            Clock.fixed(NOW, ZoneOffset.UTC), () -> "7KQ3X");
    HumanVerificationRequestContext browser = context("browser-a");
    HumanVerificationChallengeView challenge =
        service.create(application(), HumanVerificationScene.SIGN_IN, browser);

    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.SIGN_IN,
                    challenge.challengeId(),
                    "wrong1",
                    browser)))
        .isFalse();
    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.SIGN_IN,
                    challenge.challengeId(),
                    "wrong2",
                    browser)))
        .isFalse();
    assertThat(
            service.verify(
                new HumanVerificationRequest(
                    application(),
                    HumanVerificationScene.SIGN_IN,
                    challenge.challengeId(),
                    "7KQ3X",
                    browser)))
        .isTrue();
  }

  private static HumanVerificationApplication application() {
    return new HumanVerificationApplication(
        "tenant_platform", "pool_console_default", "app_console_default");
  }

  private static HumanVerificationRequestContext context(String sessionId) {
    Map<String, Object> session = new HashMap<>();
    session.put("humanVerificationSessionId", sessionId);
    return new HumanVerificationRequestContext() {
      @Override
      public Object sessionAttribute(String name) {
        return session.get(name);
      }

      @Override
      public void setSessionAttribute(String name, Object value) {
        session.put(name, value);
      }
    };
  }
}
