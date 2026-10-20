package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.*;
import com.kunal.flightbooking.dto.mapper.DtoMapper;
import com.kunal.flightbooking.dto.request.CreateAirlineRequest;
import com.kunal.flightbooking.dto.request.CreateAirportRequest;
import com.kunal.flightbooking.dto.request.CreateFlightRequest;
import com.kunal.flightbooking.dto.request.CreateScheduleRequest;
import com.kunal.flightbooking.dto.response.*;
import com.kunal.flightbooking.exception.BusinessException;
import com.kunal.flightbooking.exception.ResourceNotFoundException;
import com.kunal.flightbooking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Inventory management used by ADMIN endpoints and the dev seeder. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AirportRepository airportRepository;
    private final AirlineRepository airlineRepository;
    private final FlightRepository flightRepository;
    private final FlightScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final SeatLayoutGenerator seatLayoutGenerator;

    @Transactional
    public AirportResponse createAirport(CreateAirportRequest req) {
        if (airportRepository.existsByCode(req.code())) {
            throw BusinessException.conflict("DUPLICATE_AIRPORT", "Airport " + req.code() + " already exists");
        }
        return DtoMapper.toResponse(airportRepository.save(
                new Airport(req.code(), req.name(), req.city(), req.country())));
    }

    @Transactional
    public AirlineResponse createAirline(CreateAirlineRequest req) {
        if (airlineRepository.existsByCode(req.code())) {
            throw BusinessException.conflict("DUPLICATE_AIRLINE", "Airline " + req.code() + " already exists");
        }
        return DtoMapper.toResponse(airlineRepository.save(new Airline(req.code(), req.name())));
    }

    @Transactional
    public FlightResponse createFlight(CreateFlightRequest req) {
        if (req.sourceCode().equals(req.destinationCode())) {
            throw BusinessException.badRequest("INVALID_ROUTE", "Source and destination must differ");
        }
        if (flightRepository.existsByFlightNumber(req.flightNumber())) {
            throw BusinessException.conflict("DUPLICATE_FLIGHT", "Flight " + req.flightNumber() + " already exists");
        }
        Airline airline = airlineRepository.findByCode(req.airlineCode())
                .orElseThrow(() -> new ResourceNotFoundException("Airline", req.airlineCode()));
        Airport source = airportRepository.findByCode(req.sourceCode())
                .orElseThrow(() -> new ResourceNotFoundException("Airport", req.sourceCode()));
        Airport destination = airportRepository.findByCode(req.destinationCode())
                .orElseThrow(() -> new ResourceNotFoundException("Airport", req.destinationCode()));
        Flight flight = new Flight(req.flightNumber(), airline, source, destination, req.aircraftModel(),
                req.businessSeats(), req.economySeats());
        return DtoMapper.toResponse(flightRepository.save(flight));
    }

    /** Creates a dated departure and generates its full seat inventory in the same transaction. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.FLIGHT_SEARCH, allEntries = true)
    public ScheduleResponse createSchedule(CreateScheduleRequest req) {
        if (!req.arrivalTime().isAfter(req.departureTime())) {
            throw BusinessException.badRequest("INVALID_SCHEDULE", "arrivalTime must be after departureTime");
        }
        Flight flight = flightRepository.findDetailedById(req.flightId())
                .orElseThrow(() -> new ResourceNotFoundException("Flight", req.flightId()));
        if (scheduleRepository.existsByFlightIdAndDepartureTime(flight.getId(), req.departureTime())) {
            throw BusinessException.conflict("DUPLICATE_SCHEDULE",
                    flight.getFlightNumber() + " already departs at " + req.departureTime());
        }
        FlightSchedule schedule = scheduleRepository.save(new FlightSchedule(flight, req.departureTime(),
                req.arrivalTime(), req.economyBaseFare(), req.businessBaseFare()));
        List<Seat> seats = seatRepository.saveAll(seatLayoutGenerator.generate(schedule));
        log.info("Created schedule {} for {} with {} seats", schedule.getId(), flight.getFlightNumber(), seats.size());
        return DtoMapper.toResponse(schedule, seats.size());
    }

    @Transactional(readOnly = true)
    public List<AirportResponse> listAirports() {
        return airportRepository.findAll().stream().map(DtoMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RouteRevenueResponse> routeRevenue() {
        return bookingRepository.routeRevenueReport().stream()
                .map(r -> new RouteRevenueResponse((String) r[0], (String) r[1], ((Number) r[2]).longValue(),
                        ((Number) r[3]).longValue(), new BigDecimal(r[4].toString())))
                .toList();
    }
}
