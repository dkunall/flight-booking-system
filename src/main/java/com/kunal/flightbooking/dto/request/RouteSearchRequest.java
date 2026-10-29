package com.kunal.flightbooking.dto.request;

import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.service.RouteFinder.Objective;
import com.kunal.flightbooking.service.RouteSearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Locale;

/** Query parameters of GET /api/flights/routes. */
public record RouteSearchRequest(
        @Schema(example = "DEL") @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String from,
        @Schema(example = "BLR") @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String to,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Min(1) @Max(9) Integer passengers,
        SeatClass seatClass,
        @Schema(description = "0 = direct only", example = "1") @Min(0) @Max(2) Integer maxStops,
        Objective sortBy,
        @Min(1) @Max(20) Integer limit) {

    public RouteSearchCriteria toCriteria() {
        return new RouteSearchCriteria(from.toUpperCase(Locale.ROOT), to.toUpperCase(Locale.ROOT), date,
                passengers == null ? 1 : passengers,
                seatClass == null ? SeatClass.ECONOMY : seatClass,
                maxStops == null ? 1 : maxStops,
                sortBy == null ? Objective.PRICE : sortBy,
                limit == null ? 10 : limit);
    }
}
