package com.kunal.flightbooking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "passengers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    public Passenger(String fullName, int age, Gender gender, Seat seat, BigDecimal fare) {
        this.fullName = fullName;
        this.age = age;
        this.gender = gender;
        this.seat = seat;
        this.fare = fare;
    }

    void attachTo(Booking booking) {
        this.booking = booking;
    }
}
