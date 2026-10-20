package com.kunal.flightbooking.dto.response;

import java.io.Serializable;

public record AirportResponse(Long id, String code, String name, String city, String country) implements Serializable {
}
