package com.kunal.flightbooking.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache abstraction. The provider is chosen by profile through spring.cache.type:
 * 'simple' (ConcurrentHashMap) by default and in tests, 'redis' with a 60s TTL in the docker profile.
 * Service code only depends on @Cacheable/@CacheEvict, so swapping providers needs no code change.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
