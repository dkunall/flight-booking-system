package com.kunal.flightbooking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A flight is the recurring route template (e.g. 6E2134 DEL to BOM on an A320 with a fixed cabin layout).
 * Each dated departure of it is a {@link FlightSchedule}.
 */
@Entity
@Table(name = "flights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_number", nullable = false, unique = true, length = 10)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "airline_id")
    private Airline airline;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_airport_id")
    private Airport source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_airport_id")
    private Airport destination;

    @Column(name = "aircraft_model", nullable = false, length = 50)
    private String aircraftModel;

    @Column(name = "business_seats", nullable = false)
    private int businessSeats;

    @Column(name = "economy_seats", nullable = false)
    private int economySeats;

    public Flight(String flightNumber, Airline airline, Airport source, Airport destination,
                  String aircraftModel, int businessSeats, int economySeats) {
        if (source.getCode().equals(destination.getCode())) {
            throw new IllegalArgumentException("Source and destination must differ");
        }
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.source = source;
        this.destination = destination;
        this.aircraftModel = aircraftModel;
        this.businessSeats = businessSeats;
        this.economySeats = economySeats;
    }

    public int totalSeats(SeatClass seatClass) {
        return seatClass == SeatClass.BUSINESS ? businessSeats : economySeats;
    }
}
