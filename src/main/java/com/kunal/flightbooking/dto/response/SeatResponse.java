package com.kunal.flightbooking.dto.response;

import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.domain.SeatStatus;

public record SeatResponse(Long id, String seatNumber, SeatClass seatClass, SeatStatus status) {
}
