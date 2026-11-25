package com.aegisid.foundation.security.outbound;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class SafeOutboundAddressValidatorTest {

  @Test
  void allowsHttpsHostResolvingToPublicAddress() throws Exception {
    SafeOutboundAddressValidator validator = validatorResolving("93.184.216.34");

    assertDoesNotThrow(
        () ->
            validator.validate(
                URI.create("https://idp.example.com/.well-known/openid-configuration")));
  }

  @Test
  void rejectsNonHttpsUri() throws Exception {
    SafeOutboundAddressValidator validator = validatorResolving("93.184.216.34");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                validator.validate(
                    URI.create("http://idp.example.com/.well-known/openid-configuration")));

    assertTrue(exception.getMessage().contains("HTTPS"));
  }

  @Test
  void rejectsLoopbackLiteralAddress() {
    SafeOutboundAddressValidator validator = new SafeOutboundAddressValidator();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.validate(URI.create("https://127.0.0.1/jwks")));

    assertTrue(exception.getMessage().contains("blocked"));
  }

  @Test
  void rejectsHostResolvingToPrivateAddress() throws Exception {
    SafeOutboundAddressValidator validator = validatorResolving("10.0.0.8");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.validate(URI.create("https://idp.example.com/jwks")));

    assertTrue(exception.getMessage().contains("blocked"));
  }

  @Test
  void rejectsMetadataServiceAddress() {
    SafeOutboundAddressValidator validator = new SafeOutboundAddressValidator();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.validate(URI.create("https://169.254.169.254/latest/meta-data")));

    assertTrue(exception.getMessage().contains("blocked"));
  }

  @Test
  void rejectsIpv6UniqueLocalAddress() {
    SafeOutboundAddressValidator validator = new SafeOutboundAddressValidator();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> validator.validate(URI.create("https://[fc00::1]/jwks")));

    assertTrue(exception.getMessage().contains("blocked"));
  }

  @Test
  void canExplicitlyAllowLocalHttpForControlledSmokeProviders() throws Exception {
    SafeOutboundAddressValidator validator =
        new SafeOutboundAddressValidator(
            host -> new InetAddress[] {InetAddress.getByName("127.0.0.1")}, true, true);

    assertDoesNotThrow(
        () -> validator.validate(URI.create("http://127.0.0.1:18084/mock-oidc/token")));
  }

  private static SafeOutboundAddressValidator validatorResolving(String address) throws Exception {
    InetAddress inetAddress = InetAddress.getByName(address);
    return new SafeOutboundAddressValidator(host -> new InetAddress[] {inetAddress});
  }
}
