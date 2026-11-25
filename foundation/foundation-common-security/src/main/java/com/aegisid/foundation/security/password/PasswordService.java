package com.aegisid.foundation.security.password;

import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordService {

  private final PasswordEncoder passwordEncoder;

  public PasswordService() {
    this(new BCryptPasswordEncoder());
  }

  public PasswordService(PasswordEncoder passwordEncoder) {
    this.passwordEncoder =
        Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
  }

  public String hash(String rawPassword) {
    Objects.requireNonNull(rawPassword, "rawPassword must not be null");
    return passwordEncoder.encode(rawPassword);
  }

  public boolean matches(String rawPassword, String passwordHash) {
    Objects.requireNonNull(rawPassword, "rawPassword must not be null");
    Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    return passwordEncoder.matches(rawPassword, passwordHash);
  }
}
