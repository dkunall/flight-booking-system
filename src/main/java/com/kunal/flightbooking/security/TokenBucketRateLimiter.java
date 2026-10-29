package com.kunal.flightbooking.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Token bucket per key (client IP). Keys live in a bounded LRU map so a flood of distinct addresses can't
 * grow memory without limit. In-process only: with several replicas each one limits on its own.
 */
public class TokenBucketRateLimiter {

    /** {@code retryAfterSeconds} is 0 when allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
    }

    private final int capacity;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets;

    public TokenBucketRateLimiter(int capacity, double refillPerSecond, int maxKeys, LongSupplier nanoClock) {
        if (capacity < 1 || refillPerSecond <= 0 || maxKeys < 1) {
            throw new IllegalArgumentException("capacity, refill rate and maxKeys must be positive");
        }
        this.capacity = capacity;
        this.tokensPerNano = refillPerSecond / 1_000_000_000d;
        this.nanoClock = nanoClock;
        this.buckets = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxKeys;
            }
        };
    }

    public synchronized Decision tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            bucket = new Bucket();
            bucket.tokens = capacity;
            bucket.lastRefillNanos = now;
            buckets.put(key, bucket);
        } else {
            bucket.tokens = Math.min(capacity, bucket.tokens + (now - bucket.lastRefillNanos) * tokensPerNano);
            bucket.lastRefillNanos = now;
        }
        if (bucket.tokens >= 1) {
            bucket.tokens -= 1;
            return new Decision(true, 0);
        }
        double nanosUntilToken = (1 - bucket.tokens) / tokensPerNano;
        return new Decision(false, Math.max(1, (long) Math.ceil(nanosUntilToken / 1_000_000_000d)));
    }

    synchronized int trackedKeys() {
        return buckets.size();
    }
}
