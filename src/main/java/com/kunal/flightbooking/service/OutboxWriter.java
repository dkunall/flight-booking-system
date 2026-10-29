package com.kunal.flightbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.flightbooking.domain.OutboxEvent;
import com.kunal.flightbooking.pattern.observer.BookingEvent;
import com.kunal.flightbooking.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(BookingEvent event) {
        try {
            repository.save(new OutboxEvent(BOOKING, event.bookingId(), "BOOKING_" + event.status(),
                    objectMapper.writeValueAsString(event), LocalDateTime.now(clock)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise booking event", e);
        }
    }
}
