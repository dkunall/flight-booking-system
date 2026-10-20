package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.SeatClass;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Normalised search input. A record, so equals/hashCode make it a correct cache key. */
public record FlightSearchCriteria(
        String from,
        String to,
        LocalDate date,
        int passengers,
        SeatClass seatClass,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<String> airlines,
        SortField sortBy,
        SortDirection direction,
        int page,
        int size) implements Serializable {

    public enum SortField { PRICE, DURATION, DEPARTURE }

    public enum SortDirection { ASC, DESC }
}
