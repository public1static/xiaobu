package com.aegisid.foundation.security.humanverification;

public interface HumanVerificationProvider extends HumanVerificationChallengeVerifier {

  String provider();

  HumanVerificationChallengeView create(
      HumanVerificationApplication application,
      HumanVerificationScene scene,
      String target,
      String method,
      HumanVerificationRequestContext context);

  byte[] image(String challengeId);
}
