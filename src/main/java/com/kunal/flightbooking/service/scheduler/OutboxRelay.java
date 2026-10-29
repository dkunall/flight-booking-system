package com.kunal.flightbooking.service.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.flightbooking.domain.OutboxEvent;
import com.kunal.flightbooking.pattern.observer.BookingEvent;
import com.kunal.flightbooking.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Drains the outbox and hands each event to the in-process listeners. Delivery is at-least-once: a row is
 * marked published only after every listener returned, and failed rows are retried up to MAX_ATTEMPTS.
 * Swapping the publisher for a Kafka producer would not change anything else.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.relay-ms:1000}")
    @Transactional
    public int relayPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(MAX_ATTEMPTS, BATCH_SIZE);
        int published = 0;
        for (OutboxEvent row : batch) {
            try {
                publisher.publishEvent(objectMapper.readValue(row.getPayload(), BookingEvent.class));
                row.markPublished(LocalDateTime.now(clock));
                published++;
            } catch (Exception e) {
                log.warn("Outbox event {} failed (attempt {}): {}", row.getId(), row.getAttempts() + 1, e.getMessage());
                row.markFailed(e.getMessage());
            }
        }
        if (published > 0) {
            log.debug("Relayed {} outbox event(s)", published);
        }
        return published;
    }
}
