package com.kunal.flightbooking.support;

import com.kunal.flightbooking.domain.Role;
import com.kunal.flightbooking.domain.Seat;
import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.domain.User;
import com.kunal.flightbooking.dto.request.CreateAirlineRequest;
import com.kunal.flightbooking.dto.request.CreateAirportRequest;
import com.kunal.flightbooking.dto.request.CreateFlightRequest;
import com.kunal.flightbooking.dto.request.CreateScheduleRequest;
import com.kunal.flightbooking.dto.response.ScheduleResponse;
import com.kunal.flightbooking.repository.AirlineRepository;
import com.kunal.flightbooking.repository.AirportRepository;
import com.kunal.flightbooking.repository.SeatRepository;
import com.kunal.flightbooking.repository.UserRepository;
import com.kunal.flightbooking.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds isolated inventory per test (unique flight numbers / routes) so integration tests can share one
 * Spring context and database without interfering with each other.
 */
@TestComponent
public class TestDataFactory {

    private static final AtomicInteger FLIGHT_SEQ = new AtomicInteger(1000);

    @Autowired AdminService adminService;
    @Autowired AirportRepository airportRepository;
    @Autowired AirlineRepository airlineRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired UserRepository userRepository;

    public void ensureAirport(String code) {
        if (!airportRepository.existsByCode(code)) {
            adminService.createAirport(new CreateAirportRequest(code, code + " Airport", code + " City", "India"));
        }
    }

    public void ensureAirline(String code) {
        if (!airlineRepository.existsByCode(code)) {
            adminService.createAirline(new CreateAirlineRequest(code, "Airline " + code));
        }
    }

    public ScheduleResponse schedule(String from, String to, String airline, LocalDateTime departure,
                                     int durationMinutes, String economyFare, int businessSeats, int economySeats) {
        ensureAirport(from);
        ensureAirport(to);
        ensureAirline(airline);
        var flight = adminService.createFlight(new CreateFlightRequest(
                airline + FLIGHT_SEQ.incrementAndGet(), airline, from, to, "A320neo", businessSeats, economySeats));
        return adminService.createSchedule(new CreateScheduleRequest(flight.id(), departure,
                departure.plusMinutes(durationMinutes), new BigDecimal(economyFare), new BigDecimal("15000")));
    }

    public ScheduleResponse simpleSchedule() {
        return schedule("DEL", "BOM", "6E", LocalDateTime.now().plusDays(5).withNano(0), 130, "5000", 4, 30);
    }

    public List<Long> seatIds(Long scheduleId, SeatClass seatClass) {
        return seatRepository.findByScheduleId(scheduleId).stream()
                .filter(s -> s.getSeatClass() == seatClass).map(Seat::getId).toList();
    }

    /** Persists a user directly (no HTTP), for service-level tests such as the concurrency test. */
    public User user(Role role) {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        return userRepository.save(new User("IT User", email, "{noop}unused", "9876500000", role));
    }
}
