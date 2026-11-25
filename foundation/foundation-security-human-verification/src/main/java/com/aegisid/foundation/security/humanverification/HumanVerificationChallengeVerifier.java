package com.aegisid.foundation.security.humanverification;

public interface HumanVerificationChallengeVerifier {
  boolean verify(HumanVerificationRequest request);
}
