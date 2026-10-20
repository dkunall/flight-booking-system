package com.kunal.flightbooking.dto.mapper;

import com.kunal.flightbooking.domain.*;
import com.kunal.flightbooking.dto.response.*;

/** Entity to DTO mapping. Entities never leave the service layer. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static AirportResponse toResponse(Airport a) {
        return new AirportResponse(a.getId(), a.getCode(), a.getName(), a.getCity(), a.getCountry());
    }

    public static AirlineResponse toResponse(Airline a) {
        return new AirlineResponse(a.getId(), a.getCode(), a.getName());
    }

    public static FlightResponse toResponse(Flight f) {
        return new FlightResponse(f.getId(), f.getFlightNumber(), f.getAirline().getCode(), f.getSource().getCode(),
                f.getDestination().getCode(), f.getAircraftModel(), f.getBusinessSeats(), f.getEconomySeats());
    }

    public static ScheduleResponse toResponse(FlightSchedule s, int seatsGenerated) {
        Flight f = s.getFlight();
        return new ScheduleResponse(s.getId(), f.getFlightNumber(), f.getSource().getCode(),
                f.getDestination().getCode(), s.getDepartureTime(), s.getArrivalTime(), s.getEconomyBaseFare(),
                s.getBusinessBaseFare(), seatsGenerated);
    }

    public static SeatResponse toResponse(Seat s) {
        return new SeatResponse(s.getId(), s.getSeatNumber(), s.getSeatClass(), s.getStatus());
    }

    public static BookingResponse toResponse(Booking b) {
        FlightSchedule s = b.getSchedule();
        Flight f = s.getFlight();
        return new BookingResponse(b.getId(), b.getPnr(), b.getStatus(), s.getId(), f.getFlightNumber(),
                f.getSource().getCode(), f.getDestination().getCode(), s.getDepartureTime(), b.getSeatClass(),
                b.getTotalAmount(), b.getCouponCode(), b.getHoldExpiresAt(), b.getFailureReason(),
                b.getPassengers().stream()
                        .map(p -> new PassengerResponse(p.getFullName(), p.getAge(), p.getGender(),
                                p.getSeat().getId(), p.getSeat().getSeatNumber(), p.getFare()))
                        .toList(),
                b.getCreatedAt());
    }

    public static PaymentResponse toResponse(Payment p, Booking b) {
        return new PaymentResponse(p.getId(), b.getId(), b.getPnr(), p.getMethod(), p.getStatus(), p.getAmount(),
                p.getGatewayReference(), p.getFailureReason(), b.getStatus());
    }
}
