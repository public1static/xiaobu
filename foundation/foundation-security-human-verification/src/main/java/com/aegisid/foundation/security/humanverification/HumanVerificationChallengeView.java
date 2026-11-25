package com.aegisid.foundation.security.humanverification;

import java.time.Instant;

public record HumanVerificationChallengeView(
    String challengeId, String provider, String imageUrl, Instant expiresAt, String debugAnswer) {}
