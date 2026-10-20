package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.Flight;
import com.kunal.flightbooking.domain.FlightSchedule;
import com.kunal.flightbooking.domain.Seat;
import com.kunal.flightbooking.domain.SeatClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the seat inventory for a schedule from the flight's cabin layout.
 * Business: 4 abreast (A C D F) in the front rows; Economy: 6 abreast (A-F) behind it.
 */
@Component
public class SeatLayoutGenerator {

    private static final char[] BUSINESS_LETTERS = {'A', 'C', 'D', 'F'};
    private static final char[] ECONOMY_LETTERS = {'A', 'B', 'C', 'D', 'E', 'F'};

    public List<Seat> generate(FlightSchedule schedule) {
        Flight flight = schedule.getFlight();
        List<Seat> seats = new ArrayList<>(flight.getBusinessSeats() + flight.getEconomySeats());
        for (int i = 0; i < flight.getBusinessSeats(); i++) {
            int row = 1 + i / BUSINESS_LETTERS.length;
            seats.add(new Seat(schedule, row + "" + BUSINESS_LETTERS[i % BUSINESS_LETTERS.length], SeatClass.BUSINESS));
        }
        int businessRows = (int) Math.ceil(flight.getBusinessSeats() / (double) BUSINESS_LETTERS.length);
        for (int i = 0; i < flight.getEconomySeats(); i++) {
            int row = businessRows + 1 + i / ECONOMY_LETTERS.length;
            seats.add(new Seat(schedule, row + "" + ECONOMY_LETTERS[i % ECONOMY_LETTERS.length], SeatClass.ECONOMY));
        }
        return seats;
    }
}
