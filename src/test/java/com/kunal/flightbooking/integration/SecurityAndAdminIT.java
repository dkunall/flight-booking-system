package com.kunal.flightbooking.integration;

import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityAndAdminIT extends IntegrationTestBase {

    @Test
    void registerThenLoginReturnsJwt() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Kunal","email":"%s","password":"S3cure-pass!"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Kunal","email":"%s","password":"S3cure-pass!"}""".formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"S3cure-pass!"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"wrong-password"}""".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointsRequireTokenAndEchoCorrelationId() throws Exception {
        mvc.perform(get("/api/bookings").header("X-Correlation-Id", "test-corr-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "test-corr-123"))
                .andExpect(jsonPath("$.correlationId").value("test-corr-123"));
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUsersCannotCallAdminEndpoints() throws Exception {
        mvc.perform(post("/api/admin/airports").header("Authorization", registerUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GOI\",\"name\":\"Goa\",\"city\":\"Goa\",\"country\":\"India\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCreatesAirportsFlightAndScheduleWithGeneratedSeats() throws Exception {
        String admin = adminToken();
        for (String code : new String[]{"PNQ", "GAU"}) {
            mvc.perform(post("/api/admin/airports").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"%s\",\"name\":\"%s Intl\",\"city\":\"%s\",\"country\":\"India\"}"
                                    .formatted(code, code, code)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/admin/airlines").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"I5\",\"name\":\"AIX Connect\"}"))
                .andExpect(status().isCreated());
        long flightId = read(mvc.perform(post("/api/admin/flights").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flightNumber":"I5771","airlineCode":"I5","sourceCode":"PNQ","destinationCode":"GAU",
                                 "aircraftModel":"A320neo","businessSeats":8,"economySeats":120}"""))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        LocalDateTime dep = LocalDateTime.now().plusDays(9).withHour(7).withMinute(0).withSecond(0).withNano(0);
        String schedule = """
                {"flightId":%d,"departureTime":"%s","arrivalTime":"%s","economyBaseFare":5200,"businessBaseFare":16000}"""
                .formatted(flightId, dep, dep.plusMinutes(185));
        long scheduleId = read(mvc.perform(post("/api/admin/schedules").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatsGenerated").value(128))
                .andReturn()).get("id").asLong();

        mvc.perform(post("/api/admin/schedules").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SCHEDULE"));

        mvc.perform(get("/api/schedules/" + scheduleId + "/seats"))
                .andExpect(jsonPath("$.length()").value(128))
                .andExpect(jsonPath("$[0].seatNumber").value("1A"))
                .andExpect(jsonPath("$[0].seatClass").value("BUSINESS"))
                .andExpect(jsonPath("$[8].seatNumber").value("3A"))      // business uses rows 1-2
                .andExpect(jsonPath("$[8].seatClass").value("ECONOMY"));

        mvc.perform(get("/api/flights/search?from=PNQ&to=GAU&date=" + dep.toLocalDate()))
                .andExpect(jsonPath("$.content[0].flightNumber").value("I5771"));
    }

    @Test
    void routeRevenueReportAggregatesConfirmedBookings() throws Exception {
        var schedule = data.schedule("JAI", "IXR", "6E", LocalDateTime.now().plusDays(12).withNano(0), 120, "4000", 0, 30);
        String user = registerUser();
        Long seatId = data.seatIds(schedule.id(), SeatClass.ECONOMY).get(0);
        long bookingId = read(mvc.perform(post("/api/bookings").header("Authorization", user)
                        .header("Idempotency-Key", newIdempotencyKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId":%d,"passengers":[{"fullName":"A","age":40,"gender":"MALE","seatId":%d}]}"""
                                .formatted(schedule.id(), seatId)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(post("/api/bookings/" + bookingId + "/payments").header("Authorization", user)
                        .header("Idempotency-Key", newIdempotencyKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"UPI\",\"upiId\":\"asha@okhdfc\"}"))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));

        mvc.perform(get("/api/admin/reports/route-revenue").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sourceCode=='JAI' && @.destinationCode=='IXR')].revenue", hasItem(4000.0)))
                .andExpect(jsonPath("$[?(@.sourceCode=='JAI')].bookings", hasItem(1)));
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/flights/search']").exists());
    }
}
