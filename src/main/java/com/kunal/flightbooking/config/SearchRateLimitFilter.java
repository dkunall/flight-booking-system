package com.kunal.flightbooking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.flightbooking.exception.Problems;
import com.kunal.flightbooking.security.TokenBucketRateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Per-IP token bucket on GET /api/flights/**. Search is public and the most expensive read we have. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SearchRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final TokenBucketRateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final Counter rejected;

    public SearchRateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper, MeterRegistry meters) {
        this.properties = properties;
        this.limiter = new TokenBucketRateLimiter(properties.capacity(), properties.refillPerSecond(),
                properties.maxClients(), System::nanoTime);
        this.objectMapper = objectMapper;
        this.rejected = Counter.builder("search.rate_limited").description("Search requests rejected with 429")
                .register(meters);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !"GET".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/flights/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var decision = limiter.tryAcquire(request.getRemoteAddr());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        rejected.increment();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Problems.of(HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMITED", "Too many search requests, slow down", request.getRequestURI()));
    }
}
