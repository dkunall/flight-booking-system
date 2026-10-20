package com.kunal.flightbooking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** One dated departure of a {@link Flight}; owns its seat inventory and base fares. */
@Entity
@Table(name = "flight_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id")
    private Flight flight;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(name = "economy_base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal economyBaseFare;

    @Column(name = "business_base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal businessBaseFare;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    public FlightSchedule(Flight flight, LocalDateTime departureTime, LocalDateTime arrivalTime,
                          BigDecimal economyBaseFare, BigDecimal businessBaseFare) {
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("Arrival must be after departure");
        }
        this.flight = flight;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.economyBaseFare = economyBaseFare;
        this.businessBaseFare = businessBaseFare;
    }

    public BigDecimal baseFareFor(SeatClass seatClass) {
        return seatClass == SeatClass.BUSINESS ? businessBaseFare : economyBaseFare;
    }

    public long durationMinutes() {
        return Duration.between(departureTime, arrivalTime).toMinutes();
    }

    /** Bookable while scheduled and departure is still in the future. */
    public boolean isBookable(LocalDateTime now) {
        return status == ScheduleStatus.SCHEDULED && departureTime.isAfter(now);
    }
}
