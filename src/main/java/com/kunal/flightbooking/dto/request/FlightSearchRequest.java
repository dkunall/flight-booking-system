package com.kunal.flightbooking.dto.request;

import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.service.FlightSearchCriteria;
import com.kunal.flightbooking.service.FlightSearchCriteria.SortDirection;
import com.kunal.flightbooking.service.FlightSearchCriteria.SortField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Query parameters of GET /api/flights/search, bound and validated as one object. */
public record FlightSearchRequest(
        @Schema(example = "DEL") @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String from,
        @Schema(example = "BOM") @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String to,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Min(1) @Max(9) Integer passengers,
        SeatClass seatClass,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice,
        @Schema(description = "Comma-separated airline codes, e.g. 6E,AI") List<String> airlines,
        SortField sortBy,
        SortDirection direction,
        @Min(0) Integer page,
        @Min(1) @Max(50) Integer size) {

    public FlightSearchCriteria toCriteria() {
        List<String> airlineCodes = airlines == null ? List.of()
                : airlines.stream().filter(a -> a != null && !a.isBlank())
                  .map(a -> a.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        return new FlightSearchCriteria(
                from.toUpperCase(Locale.ROOT),
                to.toUpperCase(Locale.ROOT),
                date,
                passengers == null ? 1 : passengers,
                seatClass == null ? SeatClass.ECONOMY : seatClass,
                minPrice,
                maxPrice,
                airlineCodes,
                sortBy == null ? SortField.PRICE : sortBy,
                direction == null ? SortDirection.ASC : direction,
                page == null ? 0 : page,
                size == null ? 10 : size);
    }
}
