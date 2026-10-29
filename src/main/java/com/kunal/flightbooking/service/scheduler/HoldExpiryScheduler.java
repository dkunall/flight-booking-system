package com.kunal.flightbooking.service.scheduler;

import com.kunal.flightbooking.service.BookingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically releases seats whose 10-minute hold has lapsed without payment. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final BookingService bookingService;
    private final MeterRegistry meters;

    @Scheduled(fixedDelayString = "${app.booking.hold-expiry-check-ms:30000}",
               initialDelayString = "${app.booking.hold-expiry-check-ms:30000}")
    public int releaseExpiredHolds() {
        int released = 0;
        for (Long bookingId : bookingService.findExpiredHoldIds()) {
            try {
                if (bookingService.expireHold(bookingId)) {
                    released++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire booking {}: {}", bookingId, e.getMessage());
            }
        }
        if (released > 0) {
            meters.counter("booking.holds_expired").increment(released);
            log.info("Released {} expired seat hold(s)", released);
        }
        return released;
    }
}
