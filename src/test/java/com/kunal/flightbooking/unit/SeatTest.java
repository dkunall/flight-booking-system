package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.domain.Seat;
import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.domain.SeatStatus;
import com.kunal.flightbooking.exception.SeatUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatTest {

    private final LocalDateTime until = LocalDateTime.now().plusMinutes(10);

    @Test
    void holdConfirmRelease() {
        Seat seat = new Seat(null, "4A", SeatClass.ECONOMY);
        seat.hold(7L, until);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getBookingId()).isEqualTo(7L);

        seat.confirm(7L);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getHoldExpiresAt()).isNull();

        seat.release();
        assertThat(seat.isAvailable()).isTrue();
        assertThat(seat.getBookingId()).isNull();
    }

    @Test
    void cannotHoldAnUnavailableSeat() {
        Seat seat = new Seat(null, "4A", SeatClass.ECONOMY);
        seat.hold(1L, until);
        assertThatThrownBy(() -> seat.hold(2L, until)).isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void onlyTheHoldingBookingCanConfirm() {
        Seat seat = new Seat(null, "4A", SeatClass.ECONOMY);
        seat.hold(1L, until);
        assertThatThrownBy(() -> seat.confirm(2L)).isInstanceOf(IllegalStateException.class);
    }
}
