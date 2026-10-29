package com.kunal.flightbooking.integration;

import com.kunal.flightbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureObservability
@TestPropertySource(properties = {
        "app.rate-limit.search.enabled=true",
        "app.rate-limit.search.capacity=3",
        "app.rate-limit.search.refill-per-second=0.01"})
class OpsIT extends IntegrationTestBase {

    private static RequestPostProcessor from(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    @Test
    void searchIsRateLimitedPerClient() throws Exception {
        String url = "/api/airports";   // not limited
        for (int i = 0; i < 5; i++) {
            mvc.perform(get(url).with(from("10.0.0.1"))).andExpect(status().isOk());
        }
        String search = "/api/flights/search?from=DEL&to=BOM&date=2031-01-01";
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(search).with(from("10.0.0.1"))).andExpect(status().isOk());
        }
        mvc.perform(get(search).with(from("10.0.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        mvc.perform(get(search).with(from("10.0.0.2"))).andExpect(status().isOk());
    }

    @Test
    void probesAndPrometheusArePublic() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("outbox_pending")))
                .andExpect(content().string(containsString("search_rate_limited_total")));
    }
}
