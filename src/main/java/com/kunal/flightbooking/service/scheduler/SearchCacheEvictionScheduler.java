package com.kunal.flightbooking.service.scheduler;

import com.kunal.flightbooking.service.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The simple in-memory cache has no TTL, so search results are flushed periodically to bound staleness
 * of seat counts and dynamic fares. (Redis additionally enforces a TTL in the docker profile.)
 */
@Slf4j
@Component
public class SearchCacheEvictionScheduler {

    @Scheduled(fixedRateString = "${app.cache.search-eviction-ms:60000}")
    @CacheEvict(cacheNames = CacheNames.FLIGHT_SEARCH, allEntries = true)
    public void evictSearchCache() {
        log.debug("Evicted flight search cache");
    }
}
