package com.kunal.flightbooking.integration;

import com.kunal.flightbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RouteSearchIT extends IntegrationTestBase {

    private LocalDate date;

    @BeforeAll
    void createInventory() {
        date = LocalDate.now().plusDays(25);
        data.schedule("IXR", "IXS", "6E", date.atTime(7, 0), 180, "9000", 0, 30);   // direct
        data.schedule("IXR", "IXT", "AI", date.atTime(6, 0), 90, "3000", 0, 30);    // lands 07:30
        data.schedule("IXT", "IXS", "QP", date.atTime(9, 0), 60, "2500", 0, 30);    // 90 min layover
    }

    private String url(String extra) {
        return "/api/flights/routes?from=IXR&to=IXS&date=" + date + extra;
    }

    @Test
    void cheapestFirstIncludesConnection() throws Exception {
        mvc.perform(get(url("&passengers=2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].stops", contains(1, 0)))
                .andExpect(jsonPath("$[0].via", contains("IXT")))
                .andExpect(jsonPath("$[0].layoverMinutes", contains(90)))
                .andExpect(jsonPath("$[0].totalDurationMinutes").value(240))
                .andExpect(jsonPath("$[0].farePerPassenger").value(5500.0))
                .andExpect(jsonPath("$[0].totalFare").value(11000.0))
                .andExpect(jsonPath("$[0].legs[*].airlineCode", contains("AI", "QP")));
    }

    @Test
    void fastestFirstAndDirectOnly() throws Exception {
        mvc.perform(get(url("&sortBy=DURATION")))
                .andExpect(jsonPath("$[*].stops", contains(0, 1)));
        mvc.perform(get(url("&maxStops=0")))
                .andExpect(jsonPath("$[*].stops", contains(0)));
    }

    @Test
    void rejectsBadInput() throws Exception {
        mvc.perform(get(url("&maxStops=3"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/flights/routes?from=IXR&to=IXR&date=" + date))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTE"));
    }
}
