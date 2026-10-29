package com.kunal.flightbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.flightbooking.domain.OutboxEvent;
import com.kunal.flightbooking.pattern.observer.BookingEvent;
import com.kunal.flightbooking.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

/** Appends booking events to the outbox. Must join the caller's transaction, never open its own. */
@Service
@RequiredArgsConstructor
public class OutboxWriter {

    public static final String BOOKING = "BOOKING";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meters;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(BookingEvent event) {
        try {
            repository.save(new OutboxEvent(BOOKING, event.bookingId(), "BOOKING_" + event.status(),
                    objectMapper.writeValueAsString(event), LocalDateTime.now(clock)));
            // Count on commit only, so rolled-back transitions don't show up in the metric.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    meters.counter("booking.transitions", "status", event.status().name()).increment();
                }
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise booking event", e);
        }
    }
}
