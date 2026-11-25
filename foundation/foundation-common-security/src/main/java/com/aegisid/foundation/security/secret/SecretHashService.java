package com.aegisid.foundation.security.secret;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public class SecretHashService {

  public SecretHash hash(String rawSecret) {
    Objects.requireNonNull(rawSecret, "rawSecret must not be null");
    return new SecretHash(sha256(rawSecret));
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  public record SecretHash(String value) {

    public SecretHash {
      Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String toString() {
      return "SecretHash[value=<redacted>]";
    }
  }
}
