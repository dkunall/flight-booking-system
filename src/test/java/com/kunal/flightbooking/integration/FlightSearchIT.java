package com.kunal.flightbooking.integration;

import com.kunal.flightbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlightSearchIT extends IntegrationTestBase {

    // Dedicated route so other test classes never affect these results.
    private static final String FROM = "IXA";
    private static final String TO = "IXB";
    private LocalDate date;

    @BeforeAll
    void createInventory() {
        date = LocalDate.now().plusDays(20);
        data.schedule(FROM, TO, "6E", date.atTime(6, 0), 150, "4500", 0, 60);   // cheapest, longest
        data.schedule(FROM, TO, "AI", date.atTime(9, 0), 120, "6500", 4, 60);
        data.schedule(FROM, TO, "QP", date.atTime(14, 0), 90, "5500", 0, 60);   // shortest
        data.schedule(FROM, TO, "6E", date.plusDays(1).atTime(6, 0), 150, "3000", 0, 60); // other day
    }

    private String url(String extra) {
        return "/api/flights/search?from=" + FROM + "&to=" + TO + "&date=" + date + extra;
    }

    @Test
    void returnsOnlyFlightsOnRequestedDateSortedByPriceByDefault() throws Exception {
        mvc.perform(get(url("&passengers=2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].airlineCode", contains("6E", "QP", "AI")))
                .andExpect(jsonPath("$.content[0].farePerPassenger").value(4500.0))
                .andExpect(jsonPath("$.content[0].totalFare").value(9000.0))
                .andExpect(jsonPath("$.content[0].availableSeats").value(60));
    }

    @Test
    void sortsByDurationAndDepartureDescending() throws Exception {
        mvc.perform(get(url("&sortBy=DURATION")))
                .andExpect(jsonPath("$.content[*].durationMinutes", contains(90, 120, 150)));
        mvc.perform(get(url("&sortBy=DEPARTURE&direction=DESC")))
                .andExpect(jsonPath("$.content[*].airlineCode", contains("QP", "AI", "6E")));
    }

    @Test
    void filtersByPriceRangeAndAirline() throws Exception {
        mvc.perform(get(url("&minPrice=5000&maxPrice=6000")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].airlineCode").value("QP"));
        mvc.perform(get(url("&airlines=AI,qp")))
                .andExpect(jsonPath("$.content[*].airlineCode", containsInAnyOrder("AI", "QP")));
    }

    @Test
    void paginates() throws Exception {
        mvc.perform(get(url("&size=2&page=1")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void businessCabinOnlyListsFlightsThatHaveOne() throws Exception {
        mvc.perform(get(url("&seatClass=BUSINESS")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].airlineCode").value("AI"))
                .andExpect(jsonPath("$.content[0].farePerPassenger").value(15000.0));
    }

    @Test
    void excludesFlightsWithFewerSeatsThanPassengers() throws Exception {
        LocalDateTime dep = LocalDate.now().plusDays(21).atTime(10, 0);
        data.schedule("IXC", "IXD", "6E", dep, 60, "3000", 0, 2);
        mvc.perform(get("/api/flights/search?from=IXC&to=IXD&date=" + dep.toLocalDate() + "&passengers=3"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsInvalidQueriesWithStructuredErrors() throws Exception {
        mvc.perform(get("/api/flights/search?from=DELHI&to=BOM&date=" + date + "&passengers=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("from", "passengers")))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        mvc.perform(get("/api/flights/search?from=DEL&to=DEL&date=" + date))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTE"));
    }
}
