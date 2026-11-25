package com.aegisid.foundation.security.humanverification;

public record HumanVerificationApplication(
    String tenantId, String userPoolId, String applicationId) {}
