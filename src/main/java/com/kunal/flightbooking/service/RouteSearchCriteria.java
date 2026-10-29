package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.service.RouteFinder.Objective;

import java.io.Serializable;
import java.time.LocalDate;

/** Cache key for multi-leg search. */
public record RouteSearchCriteria(String from, String to, LocalDate date, int passengers, SeatClass seatClass,
                                  int maxStops, Objective sortBy, int limit) implements Serializable {
}
