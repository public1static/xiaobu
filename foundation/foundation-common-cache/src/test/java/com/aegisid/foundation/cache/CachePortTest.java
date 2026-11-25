package com.aegisid.foundation.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CachePortTest {

  @Test
  void cachePortStoresValuesByTtlAndReturnsMissingAsEmpty() {
    CachePort cache = new CaffeineCachePort();

    assertTrue(cache.get("missing-key", String.class).isEmpty());

    cache.put("feature:alpha", "enabled", Duration.ofMinutes(1));

    assertEquals("enabled", cache.get("feature:alpha", String.class).orElseThrow());
  }

  @Test
  void expiredValueIsRemoved() throws InterruptedException {
    CachePort cache = new CaffeineCachePort();

    cache.put("temporary:key", "gone-soon", Duration.ofMillis(10));
    Thread.sleep(30);
    cache.cleanUp();

    assertTrue(cache.get("temporary:key", String.class).isEmpty());
    assertEquals(0, cache.estimatedSize());
  }

  @Test
  void expiredReadDoesNotRemoveNewerValue() throws InterruptedException {
    CachePort cache = new CaffeineCachePort();

    cache.put("temporary:key", "old-value", Duration.ofMillis(10));
    Thread.sleep(30);
    cache.put("temporary:key", "new-value", Duration.ofMinutes(1));

    assertEquals("new-value", cache.get("temporary:key", String.class).orElseThrow());
  }

  @Test
  void cacheKeysAreStringsAndCanHideRawSecrets() {
    String rawSecret = "client-secret-value";

    String key = CachePort.secretKey("oauth:client", rawSecret);

    assertFalse(key.contains(rawSecret));

    CachePort cache = new CaffeineCachePort();
    cache.put(key, "hashed-reference", Duration.ofMinutes(1));

    assertEquals("hashed-reference", cache.get(key, String.class).orElseThrow());
  }

  @Test
  void clearRemovesAllValues() {
    CachePort cache = new CaffeineCachePort();
    cache.put("feature:alpha", "enabled", Duration.ofMinutes(1));
    cache.put("feature:beta", "disabled", Duration.ofMinutes(1));

    cache.clear();

    assertTrue(cache.get("feature:alpha", String.class).isEmpty());
    assertTrue(cache.get("feature:beta", String.class).isEmpty());
    assertEquals(0, cache.estimatedSize());
  }

  @Test
  void secretDerivedKeysRejectBlankInput() {
    assertThrows(IllegalArgumentException.class, () -> CachePort.secretKey("", "secret"));
    assertThrows(IllegalArgumentException.class, () -> CachePort.secretKey("oauth:client", " "));
  }
}
