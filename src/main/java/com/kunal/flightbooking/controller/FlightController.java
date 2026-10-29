package com.kunal.flightbooking.controller;

import com.kunal.flightbooking.dto.request.FlightSearchRequest;
import com.kunal.flightbooking.dto.request.RouteSearchRequest;
import com.kunal.flightbooking.dto.response.AirportResponse;
import com.kunal.flightbooking.dto.response.FlightSearchResult;
import com.kunal.flightbooking.dto.response.PageResponse;
import com.kunal.flightbooking.dto.response.RouteOption;
import com.kunal.flightbooking.dto.response.SeatResponse;
import com.kunal.flightbooking.service.AdminService;
import com.kunal.flightbooking.service.FlightSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Flights")
@RestController
@RequiredArgsConstructor
public class FlightController {

    private final FlightSearchService searchService;
    private final AdminService adminService;

    @Operation(summary = "Search flights by route/date with price & airline filters, sorting and pagination")
    @GetMapping("/api/flights/search")
    public PageResponse<FlightSearchResult> search(@Valid @ParameterObject FlightSearchRequest request) {
        return searchService.search(request.toCriteria());
    }

    @Operation(summary = "Direct and connecting itineraries (up to 2 stops), cheapest or fastest first")
    @GetMapping("/api/flights/routes")
    public List<RouteOption> routes(@Valid @ParameterObject RouteSearchRequest request) {
        return searchService.routes(request.toCriteria());
    }

    @Operation(summary = "Seat map for a schedule")
    @GetMapping("/api/schedules/{scheduleId}/seats")
    public List<SeatResponse> seats(@PathVariable Long scheduleId) {
        return searchService.seatMap(scheduleId);
    }

    @Operation(summary = "List airports")
    @GetMapping("/api/airports")
    public List<AirportResponse> airports() {
        return adminService.listAirports();
    }
}
