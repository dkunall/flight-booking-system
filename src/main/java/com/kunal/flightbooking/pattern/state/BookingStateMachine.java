package com.kunal.flightbooking.pattern.state;

import com.kunal.flightbooking.domain.BookingStatus;
import com.kunal.flightbooking.exception.InvalidStateTransitionException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.kunal.flightbooking.domain.BookingStatus.*;

/**
 * Explicit, table-driven state machine for the booking lifecycle.
 *
 * <pre>
 * INITIATED -> SEATS_HELD -> PAYMENT_PENDING -> CONFIRMED -> CANCELLED -> REFUNDED
 *     |            |   \             |
 *     v            v    -> CANCELLED v
 *   FAILED       FAILED (hold expiry) FAILED (payment declined) -> REFUNDED (if money was captured)
 * </pre>
 *
 * Keeping transitions in one immutable table (instead of if/else scattered across services) makes the
 * rules easy to audit, unit-test exhaustively and extend (Open/Closed).
 */
public final class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        TRANSITIONS.put(INITIATED, EnumSet.of(SEATS_HELD, FAILED));
        TRANSITIONS.put(SEATS_HELD, EnumSet.of(PAYMENT_PENDING, CANCELLED, FAILED));
        TRANSITIONS.put(PAYMENT_PENDING, EnumSet.of(CONFIRMED, FAILED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(CANCELLED));
        TRANSITIONS.put(CANCELLED, EnumSet.of(REFUNDED));
        TRANSITIONS.put(FAILED, EnumSet.of(REFUNDED));
        TRANSITIONS.put(REFUNDED, EnumSet.noneOf(BookingStatus.class));
    }

    private BookingStateMachine() {
    }

    public static boolean canTransition(BookingStatus from, BookingStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(BookingStatus from, BookingStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }

    public static Set<BookingStatus> allowedTransitions(BookingStatus from) {
        return Collections.unmodifiableSet(TRANSITIONS.getOrDefault(from, Set.of()));
    }

    public static boolean isTerminal(BookingStatus status) {
        return allowedTransitions(status).isEmpty();
    }
}
