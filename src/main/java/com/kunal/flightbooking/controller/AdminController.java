package com.kunal.flightbooking.controller;

import com.kunal.flightbooking.dto.request.CreateAirlineRequest;
import com.kunal.flightbooking.dto.request.CreateAirportRequest;
import com.kunal.flightbooking.dto.request.CreateFlightRequest;
import com.kunal.flightbooking.dto.request.CreateScheduleRequest;
import com.kunal.flightbooking.dto.response.*;
import com.kunal.flightbooking.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/airports")
    @ResponseStatus(HttpStatus.CREATED)
    public AirportResponse createAirport(@Valid @RequestBody CreateAirportRequest request) {
        return adminService.createAirport(request);
    }

    @PostMapping("/airlines")
    @ResponseStatus(HttpStatus.CREATED)
    public AirlineResponse createAirline(@Valid @RequestBody CreateAirlineRequest request) {
        return adminService.createAirline(request);
    }

    @PostMapping("/flights")
    @ResponseStatus(HttpStatus.CREATED)
    public FlightResponse createFlight(@Valid @RequestBody CreateFlightRequest request) {
        return adminService.createFlight(request);
    }

    @Operation(summary = "Create a dated departure; its seat inventory is generated automatically")
    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse createSchedule(@Valid @RequestBody CreateScheduleRequest request) {
        return adminService.createSchedule(request);
    }

    @Operation(summary = "Revenue per route from confirmed bookings (native SQL aggregation)")
    @GetMapping("/reports/route-revenue")
    public List<RouteRevenueResponse> routeRevenue() {
        return adminService.routeRevenue();
    }
}
