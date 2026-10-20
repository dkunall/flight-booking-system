package com.kunal.flightbooking.pattern.observer;

import com.kunal.flightbooking.domain.BookingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.Set;

/** Mock SMS observer: only for high-signal states and only when the user has a phone number. */
@Slf4j
@Component
public class SmsNotificationListener {

    private static final Set<BookingStatus> SMS_WORTHY =
            EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.REFUNDED);

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onBookingEvent(BookingEvent event) {
        if (event.phone() == null || !SMS_WORTHY.contains(event.status())) {
            return;
        }
        log.info("[MOCK-SMS] to={} text=\"PNR {} is now {}\"", mask(event.phone()), event.pnr(), event.status());
    }

    private static String mask(String phone) {
        return phone.length() <= 4 ? "****" : "******" + phone.substring(phone.length() - 4);
    }
}
