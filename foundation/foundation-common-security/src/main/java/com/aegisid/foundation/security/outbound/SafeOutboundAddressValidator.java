package com.aegisid.foundation.security.outbound;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

public class SafeOutboundAddressValidator {

  private final AddressResolver resolver;
  private final boolean allowHttp;
  private final boolean allowPrivateAddresses;

  public SafeOutboundAddressValidator() {
    this(false, false);
  }

  public SafeOutboundAddressValidator(boolean allowHttp, boolean allowPrivateAddresses) {
    this(InetAddress::getAllByName, allowHttp, allowPrivateAddresses);
  }

  SafeOutboundAddressValidator(AddressResolver resolver) {
    this(resolver, false, false);
  }

  SafeOutboundAddressValidator(
      AddressResolver resolver, boolean allowHttp, boolean allowPrivateAddresses) {
    this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    this.allowHttp = allowHttp;
    this.allowPrivateAddresses = allowPrivateAddresses;
  }

  public void validate(URI uri) {
    Objects.requireNonNull(uri, "uri must not be null");
    String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"https".equals(scheme) && !(allowHttp && "http".equals(scheme))) {
      throw new IllegalArgumentException("Outbound request URI must use HTTPS");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new IllegalArgumentException("Outbound request URI must include a host");
    }
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Outbound request URI must not include user info");
    }
    InetAddress[] addresses = resolve(uri.getHost());
    if (addresses.length == 0) {
      throw new IllegalArgumentException("Outbound request host did not resolve to an address");
    }
    for (InetAddress address : addresses) {
      if (!allowPrivateAddresses && isBlockedAddress(address)) {
        throw new IllegalArgumentException("Outbound request host resolved to a blocked address");
      }
    }
  }

  private InetAddress[] resolve(String host) {
    try {
      return resolver.resolve(host);
    } catch (UnknownHostException exception) {
      throw new IllegalArgumentException("Outbound request host could not be resolved", exception);
    }
  }

  private static boolean isBlockedAddress(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isCarrierGradeNat(address)
        || isBenchmarkNetwork(address)
        || isIpv6UniqueLocal(address);
  }

  private static boolean isCarrierGradeNat(InetAddress address) {
    if (!(address instanceof Inet4Address)) {
      return false;
    }
    byte[] bytes = address.getAddress();
    int first = bytes[0] & 0xff;
    int second = bytes[1] & 0xff;
    return first == 100 && second >= 64 && second <= 127;
  }

  private static boolean isBenchmarkNetwork(InetAddress address) {
    if (!(address instanceof Inet4Address)) {
      return false;
    }
    byte[] bytes = address.getAddress();
    int first = bytes[0] & 0xff;
    int second = bytes[1] & 0xff;
    return first == 198 && (second == 18 || second == 19);
  }

  private static boolean isIpv6UniqueLocal(InetAddress address) {
    if (!(address instanceof Inet6Address)) {
      return false;
    }
    byte[] bytes = address.getAddress();
    return (bytes[0] & 0xfe) == 0xfc;
  }

  @FunctionalInterface
  interface AddressResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }
}
