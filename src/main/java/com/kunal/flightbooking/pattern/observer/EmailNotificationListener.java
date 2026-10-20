package com.kunal.flightbooking.pattern.observer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Mock email observer. Runs after commit and asynchronously, so a slow provider never blocks booking. */
@Slf4j
@Component
public class EmailNotificationListener {

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onBookingEvent(BookingEvent event) {
        String subject = switch (event.status()) {
            case SEATS_HELD -> "Seats held for 10 minutes - complete payment for PNR " + event.pnr();
            case CONFIRMED -> "Booking confirmed - PNR " + event.pnr();
            case FAILED -> "Booking failed - PNR " + event.pnr();
            case CANCELLED -> "Booking cancelled - PNR " + event.pnr();
            case REFUNDED -> "Refund of INR " + event.amount() + " initiated - PNR " + event.pnr();
            default -> null;
        };
        if (subject != null) {
            log.info("[MOCK-EMAIL] to={} subject=\"{}\"", event.email(), subject);
        }
    }
}
