package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.domain.BookingStatus;
import com.kunal.flightbooking.exception.InvalidStateTransitionException;
import com.kunal.flightbooking.pattern.state.BookingStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.kunal.flightbooking.domain.BookingStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class BookingStateMachineTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "INITIATED, SEATS_HELD", "INITIATED, FAILED",
            "SEATS_HELD, PAYMENT_PENDING", "SEATS_HELD, CANCELLED", "SEATS_HELD, FAILED",
            "PAYMENT_PENDING, CONFIRMED", "PAYMENT_PENDING, FAILED",
            "CONFIRMED, CANCELLED", "CANCELLED, REFUNDED", "FAILED, REFUNDED"})
    void allowsValidTransitions(BookingStatus from, BookingStatus to) {
        assertThat(BookingStateMachine.canTransition(from, to)).isTrue();
        assertThatNoException().isThrownBy(() -> BookingStateMachine.validateTransition(from, to));
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "INITIATED, CONFIRMED", "INITIATED, PAYMENT_PENDING",
            "SEATS_HELD, CONFIRMED", "SEATS_HELD, REFUNDED",
            "PAYMENT_PENDING, CANCELLED", "PAYMENT_PENDING, SEATS_HELD",
            "CONFIRMED, PAYMENT_PENDING", "CONFIRMED, REFUNDED", "CONFIRMED, FAILED",
            "CANCELLED, CONFIRMED", "FAILED, SEATS_HELD", "REFUNDED, CONFIRMED", "REFUNDED, CANCELLED"})
    void rejectsInvalidTransitions(BookingStatus from, BookingStatus to) {
        assertThat(BookingStateMachine.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> BookingStateMachine.validateTransition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining(from.name()).hasMessageContaining(to.name());
    }

    @Test
    void noStateCanTransitionToItself() {
        for (BookingStatus s : BookingStatus.values()) {
            assertThat(BookingStateMachine.canTransition(s, s)).as(s.name()).isFalse();
        }
    }

    @Test
    void refundedIsTheOnlyTerminalState() {
        for (BookingStatus s : BookingStatus.values()) {
            assertThat(BookingStateMachine.isTerminal(s)).as(s.name()).isEqualTo(s == REFUNDED);
        }
    }
}
