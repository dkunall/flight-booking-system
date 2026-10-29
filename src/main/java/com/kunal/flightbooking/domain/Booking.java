package com.kunal.flightbooking.domain;

import com.kunal.flightbooking.pattern.state.BookingStateMachine;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for a reservation. The status field has no public setter: every change goes through
 * {@link #transitionTo(BookingStatus)}, which asks the state machine whether the move is legal.
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false, unique = true, length = 8)
    private String pnr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id")
    private FlightSchedule schedule;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "seat_class", nullable = false, length = 10)
    private SeatClass seatClass;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.INITIATED;

    @Setter
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Setter
    @Column(name = "coupon_code", length = 20)
    private String couponCode;

    @Setter
    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Setter
    @Column(name = "failure_reason")
    private String failureReason;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Passenger> passengers = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Booking(String pnr, User user, FlightSchedule schedule, SeatClass seatClass) {
        this.pnr = pnr;
        this.user = user;
        this.schedule = schedule;
        this.seatClass = seatClass;
    }

    /** Guarded transition: throws InvalidStateTransitionException for illegal moves. */
    public void transitionTo(BookingStatus target) {
        BookingStateMachine.validateTransition(this.status, target);
        this.status = target;
    }

    public void addPassenger(Passenger passenger) {
        passenger.attachTo(this);
        passengers.add(passenger);
    }

    public List<Passenger> getPassengers() {
        return Collections.unmodifiableList(passengers);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public boolean isHoldExpired(LocalDateTime now) {
        return status == BookingStatus.SEATS_HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
    }
}
