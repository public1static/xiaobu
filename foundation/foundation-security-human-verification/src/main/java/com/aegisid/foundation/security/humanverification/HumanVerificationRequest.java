package com.aegisid.foundation.security.humanverification;

public record HumanVerificationRequest(
    HumanVerificationApplication application,
    HumanVerificationScene scene,
    String target,
    String method,
    String challengeId,
    String answer,
    HumanVerificationRequestContext context) {

  public HumanVerificationRequest(
      HumanVerificationApplication application,
      HumanVerificationScene scene,
      String challengeId,
      String answer,
      HumanVerificationRequestContext context) {
    this(application, scene, null, null, challengeId, answer, context);
  }
}
