package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.security.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    private final AtomicLong nanos = new AtomicLong();

    @Test
    void allowsBurstThenRejectsWithRetryAfterThenRefills() {
        var limiter = new TokenBucketRateLimiter(3, 1.0, 100, nanos::get);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        }
        var rejected = limiter.tryAcquire("a");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(1);

        nanos.addAndGet(1_000_000_000L);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
    }

    @Test
    void clientsHaveSeparateBucketsAndOldOnesAreEvicted() {
        var limiter = new TokenBucketRateLimiter(1, 0.001, 2, nanos::get);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
        assertThat(limiter.tryAcquire("c").allowed()).isTrue();   // evicts "a" (least recently used)
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();   // fresh bucket
    }
}
