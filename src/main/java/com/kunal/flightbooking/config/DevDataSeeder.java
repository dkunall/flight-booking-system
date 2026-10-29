package com.kunal.flightbooking.config;

import com.kunal.flightbooking.domain.Role;
import com.kunal.flightbooking.domain.User;
import com.kunal.flightbooking.dto.request.CreateAirlineRequest;
import com.kunal.flightbooking.dto.request.CreateAirportRequest;
import com.kunal.flightbooking.dto.request.CreateFlightRequest;
import com.kunal.flightbooking.dto.request.CreateScheduleRequest;
import com.kunal.flightbooking.repository.AirportRepository;
import com.kunal.flightbooking.repository.UserRepository;
import com.kunal.flightbooking.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Seeds Indian airports, airlines, routes and 7 days of schedules in the 'dev' profile.
 * An ADMIN account is only created when SEED_ADMIN_PASSWORD is provided (no hard-coded passwords).
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final AdminService adminService;
    private final AirportRepository airportRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email}")
    private String adminEmail;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    private record Route(String flightNumber, String airline, String from, String to, LocalTime departs,
                         int minutes, String economyFare, String businessFare) {
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
        if (airportRepository.existsByCode("DEL")) {
            log.info("Seed data already present, skipping");
            return;
        }
        List.of(
                new CreateAirportRequest("DEL", "Indira Gandhi International Airport", "New Delhi", "India"),
                new CreateAirportRequest("BOM", "Chhatrapati Shivaji Maharaj International Airport", "Mumbai", "India"),
                new CreateAirportRequest("BLR", "Kempegowda International Airport", "Bengaluru", "India"),
                new CreateAirportRequest("MAA", "Chennai International Airport", "Chennai", "India"),
                new CreateAirportRequest("HYD", "Rajiv Gandhi International Airport", "Hyderabad", "India"),
                new CreateAirportRequest("CCU", "Netaji Subhas Chandra Bose International Airport", "Kolkata", "India")
        ).forEach(adminService::createAirport);

        List.of(new CreateAirlineRequest("6E", "IndiGo"),
                new CreateAirlineRequest("AI", "Air India"),
                new CreateAirlineRequest("QP", "Akasa Air")
        ).forEach(adminService::createAirline);

        List<Route> routes = List.of(
                new Route("6E2134", "6E", "DEL", "BOM", LocalTime.of(6, 0), 130, "4899", "14999"),
                new Route("AI805", "AI", "DEL", "BOM", LocalTime.of(9, 30), 135, "5499", "16999"),
                new Route("QP1128", "QP", "DEL", "BOM", LocalTime.of(18, 45), 125, "4599", "13999"),
                new Route("6E5321", "6E", "BOM", "DEL", LocalTime.of(7, 15), 130, "4799", "14499"),
                new Route("6E6113", "6E", "BLR", "DEL", LocalTime.of(8, 0), 165, "5999", "17999"),
                new Route("AI503", "AI", "BLR", "DEL", LocalTime.of(20, 10), 170, "6299", "18999"),
                new Route("QP1402", "QP", "BOM", "BLR", LocalTime.of(11, 20), 100, "3899", "11999"),
                new Route("6E7461", "6E", "MAA", "HYD", LocalTime.of(13, 5), 75, "2999", "8999"),
                new Route("AI763", "AI", "CCU", "DEL", LocalTime.of(16, 40), 140, "5299", "15499"),
                new Route("6E2005", "6E", "DEL", "HYD", LocalTime.of(12, 30), 130, "4399", "12999"),
                new Route("AI439", "AI", "DEL", "MAA", LocalTime.of(21, 0), 165, "5199", "15999"));

        for (Route r : routes) {
            var flight = adminService.createFlight(new CreateFlightRequest(r.flightNumber(), r.airline(), r.from(),
                    r.to(), "Airbus A320neo", 12, 150));
            for (int day = 1; day <= 7; day++) {
                var departure = LocalDate.now().plusDays(day).atTime(r.departs());
                adminService.createSchedule(new CreateScheduleRequest(flight.id(), departure,
                        departure.plusMinutes(r.minutes()), new BigDecimal(r.economyFare()),
                        new BigDecimal(r.businessFare())));
            }
        }
        log.info("Seeded 6 airports, 3 airlines, {} flights x 7 days of schedules", routes.size());
    }

    private void seedAdmin() {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("SEED_ADMIN_PASSWORD not set: no ADMIN user seeded");
            return;
        }
        if (!userRepository.existsByEmailIgnoreCase(adminEmail)) {
            userRepository.save(new User("Admin", adminEmail, passwordEncoder.encode(adminPassword), null, Role.ADMIN));
            log.info("Seeded ADMIN user {}", adminEmail);
        }
    }
}
