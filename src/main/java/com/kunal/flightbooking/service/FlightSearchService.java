package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.Flight;
import com.kunal.flightbooking.domain.FlightSchedule;
import com.kunal.flightbooking.domain.ScheduleStatus;
import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.domain.SeatStatus;
import com.kunal.flightbooking.dto.mapper.DtoMapper;
import com.kunal.flightbooking.dto.response.FlightSearchResult;
import com.kunal.flightbooking.dto.response.PageResponse;
import com.kunal.flightbooking.dto.response.RouteOption;
import com.kunal.flightbooking.dto.response.SeatResponse;
import com.kunal.flightbooking.exception.BusinessException;
import com.kunal.flightbooking.exception.ResourceNotFoundException;
import com.kunal.flightbooking.pattern.strategy.FareCalculator;
import com.kunal.flightbooking.pattern.strategy.FareContext;
import com.kunal.flightbooking.repository.FlightScheduleRepository;
import com.kunal.flightbooking.repository.SeatRepository;
import com.kunal.flightbooking.service.FlightSearchCriteria.SortDirection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flight search. The database narrows candidates by route, date window and airline (indexed), and one
 * GROUP BY query fetches seat availability for all candidates. Fares are dynamic (they depend on
 * occupancy), so price filtering, sorting and pagination run in memory over that small candidate set
 * (a route has tens of departures per day, not thousands).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchService {

    private final FlightScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final FareCalculator fareCalculator;
    private final Clock clock;

    static final Duration MIN_CONNECTION = Duration.ofMinutes(45);
    static final Duration MAX_LAYOVER = Duration.ofHours(6);

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.FLIGHT_SEARCH, key = "#criteria")
    public PageResponse<FlightSearchResult> search(FlightSearchCriteria criteria) {
        if (criteria.from().equals(criteria.to())) {
            throw BusinessException.badRequest("INVALID_ROUTE", "Origin and destination must differ");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw BusinessException.badRequest("INVALID_PRICE_RANGE", "minPrice must be <= maxPrice");
        }
        log.debug("Cache miss, searching {}", criteria);

        LocalDateTime start = criteria.date().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        LocalDateTime now = LocalDateTime.now(clock);

        List<FlightSchedule> schedules = criteria.airlines().isEmpty()
                ? scheduleRepository.findForRoute(criteria.from(), criteria.to(), start, end, ScheduleStatus.SCHEDULED)
                : scheduleRepository.findForRouteAndAirlines(criteria.from(), criteria.to(), start, end,
                ScheduleStatus.SCHEDULED, criteria.airlines());
        schedules = schedules.stream().filter(s -> s.isBookable(now)).toList();
        if (schedules.isEmpty()) {
            return PageResponse.of(List.of(), criteria.page(), criteria.size());
        }

        Map<Long, Map<SeatClass, Integer>> availability =
                availableSeats(schedules.stream().map(FlightSchedule::getId).toList());

        List<FlightSearchResult> results = schedules.stream()
                .map(s -> toResult(s, criteria, availability.getOrDefault(s.getId(), Map.of())
                        .getOrDefault(criteria.seatClass(), 0)))
                .filter(r -> r.availableSeats() >= criteria.passengers())
                .filter(r -> criteria.minPrice() == null || r.farePerPassenger().compareTo(criteria.minPrice()) >= 0)
                .filter(r -> criteria.maxPrice() == null || r.farePerPassenger().compareTo(criteria.maxPrice()) <= 0)
                .sorted(comparator(criteria))
                .toList();

        return PageResponse.of(results, criteria.page(), criteria.size());
    }

    /** Itineraries with up to 2 connections, built by {@link RouteFinder} over the next ~2 days of flights. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.ROUTE_SEARCH, key = "#criteria")
    public List<RouteOption> routes(RouteSearchCriteria criteria) {
        if (criteria.from().equals(criteria.to())) {
            throw BusinessException.badRequest("INVALID_ROUTE", "Origin and destination must differ");
        }
        LocalDateTime start = criteria.date().atStartOfDay();
        LocalDateTime now = LocalDateTime.now(clock);
        List<FlightSchedule> schedules = scheduleRepository
                .findDepartingBetween(start, start.plusDays(2), ScheduleStatus.SCHEDULED).stream()
                .filter(s -> s.isBookable(now))
                .toList();
        if (schedules.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<SeatClass, Integer>> availability =
                availableSeats(schedules.stream().map(FlightSchedule::getId).toList());
        FlightSearchCriteria fareInput = new FlightSearchCriteria(criteria.from(), criteria.to(), criteria.date(),
                criteria.passengers(), criteria.seatClass(), null, null, List.of(), null, null, 0, 1);

        Map<Long, FlightSearchResult> byId = new HashMap<>();
        List<RouteFinder.Leg> legs = new ArrayList<>();
        for (FlightSchedule s : schedules) {
            int seats = availability.getOrDefault(s.getId(), Map.of()).getOrDefault(criteria.seatClass(), 0);
            if (seats < criteria.passengers()) {
                continue;
            }
            FlightSearchResult r = toResult(s, fareInput, seats);
            byId.put(r.scheduleId(), r);
            legs.add(new RouteFinder.Leg(r.scheduleId(), r.sourceCode(), r.destinationCode(), r.departureTime(),
                    r.arrivalTime(), r.farePerPassenger()));
        }

        var rules = new RouteFinder.Rules(criteria.maxStops(), MIN_CONNECTION, MAX_LAYOVER, criteria.limit(),
                criteria.sortBy());
        return new RouteFinder(legs).find(criteria.from(), criteria.to(), start, start.plusDays(1), rules).stream()
                .map(path -> toOption(path.stream().map(l -> byId.get(l.id())).toList(), criteria.passengers()))
                .toList();
    }

    private static RouteOption toOption(List<FlightSearchResult> legs, int passengers) {
        FlightSearchResult first = legs.get(0);
        FlightSearchResult last = legs.get(legs.size() - 1);
        BigDecimal fare = legs.stream().map(FlightSearchResult::farePerPassenger).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> layovers = new ArrayList<>();
        for (int i = 1; i < legs.size(); i++) {
            layovers.add(Duration.between(legs.get(i - 1).arrivalTime(), legs.get(i).departureTime()).toMinutes());
        }
        return new RouteOption(legs.size() - 1,
                legs.subList(1, legs.size()).stream().map(FlightSearchResult::sourceCode).toList(),
                first.departureTime(), last.arrivalTime(),
                Duration.between(first.departureTime(), last.arrivalTime()).toMinutes(),
                List.copyOf(layovers), fare, fare.multiply(BigDecimal.valueOf(passengers)), List.copyOf(legs));
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> seatMap(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new ResourceNotFoundException("Schedule", scheduleId);
        }
        return seatRepository.findByScheduleId(scheduleId).stream().map(DtoMapper::toResponse).toList();
    }

    private Map<Long, Map<SeatClass, Integer>> availableSeats(List<Long> scheduleIds) {
        Map<Long, Map<SeatClass, Integer>> result = new HashMap<>();
        for (Object[] row : seatRepository.countBySchedulesAndStatus(scheduleIds, SeatStatus.AVAILABLE)) {
            result.computeIfAbsent((Long) row[0], k -> new EnumMap<>(SeatClass.class))
                    .put((SeatClass) row[1], ((Number) row[2]).intValue());
        }
        return result;
    }

    private FlightSearchResult toResult(FlightSchedule s, FlightSearchCriteria c, int available) {
        Flight f = s.getFlight();
        BigDecimal fare = fareCalculator.calculate(new FareContext(c.seatClass(), s.baseFareFor(c.seatClass()),
                f.totalSeats(c.seatClass()), available, null));
        return new FlightSearchResult(s.getId(), f.getFlightNumber(), f.getAirline().getCode(),
                f.getAirline().getName(), f.getSource().getCode(), f.getDestination().getCode(),
                s.getDepartureTime(), s.getArrivalTime(), s.durationMinutes(), c.seatClass(), available, fare,
                fare.multiply(BigDecimal.valueOf(c.passengers())));
    }

    private static Comparator<FlightSearchResult> comparator(FlightSearchCriteria c) {
        Comparator<FlightSearchResult> primary = switch (c.sortBy()) {
            case PRICE -> Comparator.comparing(FlightSearchResult::farePerPassenger);
            case DURATION -> Comparator.comparingLong(FlightSearchResult::durationMinutes);
            case DEPARTURE -> Comparator.comparing(FlightSearchResult::departureTime);
        };
        if (c.direction() == SortDirection.DESC) {
            primary = primary.reversed();
        }
        // Deterministic tie-breakers keep pagination stable across requests.
        return primary.thenComparing(FlightSearchResult::departureTime)
                .thenComparing(FlightSearchResult::scheduleId);
    }
}
