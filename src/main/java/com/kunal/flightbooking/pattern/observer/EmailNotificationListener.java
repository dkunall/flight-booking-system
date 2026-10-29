package com.kunal.flightbooking.pattern.observer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

/** Mock email observer. Fed by the outbox relay, so it only ever sees committed state changes. */
@Slf4j
@Component
public class EmailNotificationListener {

    @EventListener
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
