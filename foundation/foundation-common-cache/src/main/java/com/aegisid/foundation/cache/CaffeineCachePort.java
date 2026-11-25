package com.aegisid.foundation.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class CaffeineCachePort implements CachePort {

  private final Cache<String, CacheEntry> cache;
  private final Clock clock;

  public CaffeineCachePort() {
    this(Caffeine.newBuilder().expireAfter(new PerEntryExpiry()).build(), Clock.systemUTC());
  }

  CaffeineCachePort(Cache<String, CacheEntry> cache, Clock clock) {
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public <T> Optional<T> get(String key, Class<T> type) {
    requireKey(key);
    Objects.requireNonNull(type, "type must not be null");

    CacheEntry entry = cache.getIfPresent(key);
    if (entry == null) {
      return Optional.empty();
    }
    if (entry.isExpired(clock.instant())) {
      cache.asMap().remove(key, entry);
      return Optional.empty();
    }
    if (!type.isInstance(entry.value())) {
      return Optional.empty();
    }
    return Optional.of(type.cast(entry.value()));
  }

  @Override
  public void put(String key, Object value, Duration ttl) {
    requireKey(key);
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }

    cache.put(key, new CacheEntry(value, ttl.toNanos(), clock.instant().plus(ttl)));
  }

  @Override
  public <T> T update(String key, Class<T> type, Duration ttl, Function<Optional<T>, T> updater) {
    requireKey(key);
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(ttl, "ttl must not be null");
    Objects.requireNonNull(updater, "updater must not be null");
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }

    CacheEntry updated =
        cache
            .asMap()
            .compute(
                key,
                (currentKey, currentEntry) -> {
                  Optional<T> currentValue = Optional.empty();
                  if (currentEntry != null
                      && !currentEntry.isExpired(clock.instant())
                      && type.isInstance(currentEntry.value())) {
                    currentValue = Optional.of(type.cast(currentEntry.value()));
                  }
                  T nextValue =
                      Objects.requireNonNull(
                          updater.apply(currentValue), "updater result must not be null");
                  return new CacheEntry(nextValue, ttl.toNanos(), clock.instant().plus(ttl));
                });
    return type.cast(updated.value());
  }

  @Override
  public void evict(String key) {
    requireKey(key);
    cache.invalidate(key);
  }

  @Override
  public void clear() {
    cache.invalidateAll();
  }

  @Override
  public void cleanUp() {
    cache.cleanUp();
    Instant now = clock.instant();
    cache.asMap().entrySet().removeIf(entry -> entry.getValue().isExpired(now));
  }

  @Override
  public long estimatedSize() {
    cleanUp();
    return cache.asMap().entrySet().stream().count();
  }

  private static void requireKey(String key) {
    Objects.requireNonNull(key, "key must not be null");
    if (key.isBlank()) {
      throw new IllegalArgumentException("key must not be blank");
    }
  }

  record CacheEntry(Object value, long ttlNanos, Instant expiresAt) {

    boolean isExpired(Instant now) {
      return !expiresAt.isAfter(now);
    }
  }

  private static final class PerEntryExpiry implements Expiry<String, CacheEntry> {

    @Override
    public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
      return value.ttlNanos();
    }

    @Override
    public long expireAfterUpdate(
        String key, CacheEntry value, long currentTime, long currentDuration) {
      return value.ttlNanos();
    }

    @Override
    public long expireAfterRead(
        String key, CacheEntry value, long currentTime, long currentDuration) {
      return currentDuration;
    }
  }
}
