package com.kunal.flightbooking.domain;

import com.kunal.flightbooking.exception.SeatUnavailableException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A physical seat on one schedule. State changes go through intention-revealing methods
 * (hold / confirm / release) so the AVAILABLE -> HELD -> BOOKED invariants live in one place.
 * Writes happen under a PESSIMISTIC_WRITE row lock; {@code @Version} is a second line of defence.
 */
@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id")
    private FlightSchedule schedule;

    @Column(name = "seat_number", nullable = false, length = 5)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "seat_class", nullable = false, length = 10)
    private SeatClass seatClass;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private SeatStatus status = SeatStatus.AVAILABLE;

    /** Booking currently holding or owning this seat (null when AVAILABLE). */
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Version
    private Long version;

    public Seat(FlightSchedule schedule, String seatNumber, SeatClass seatClass) {
        this.schedule = schedule;
        this.seatNumber = seatNumber;
        this.seatClass = seatClass;
    }

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public void hold(Long bookingId, LocalDateTime until) {
        if (!isAvailable()) {
            throw new SeatUnavailableException(seatNumber);
        }
        this.status = SeatStatus.HELD;
        this.bookingId = Objects.requireNonNull(bookingId);
        this.holdExpiresAt = Objects.requireNonNull(until);
    }

    public void confirm(Long bookingId) {
        if (status != SeatStatus.HELD || !Objects.equals(this.bookingId, bookingId)) {
            throw new IllegalStateException("Seat " + seatNumber + " is not held by booking " + bookingId);
        }
        this.status = SeatStatus.BOOKED;
        this.holdExpiresAt = null;
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.bookingId = null;
        this.holdExpiresAt = null;
    }
}
