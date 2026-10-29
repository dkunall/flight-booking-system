package com.kunal.flightbooking.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunal.flightbooking.domain.BookingStatus;
import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.dto.response.ScheduleResponse;
import com.kunal.flightbooking.pattern.observer.BookingEvent;
import com.kunal.flightbooking.repository.BookingRepository;
import com.kunal.flightbooking.repository.OutboxEventRepository;
import com.kunal.flightbooking.repository.PaymentRepository;
import com.kunal.flightbooking.service.OutboxWriter;
import com.kunal.flightbooking.service.scheduler.OutboxRelay;
import com.kunal.flightbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Hold -> pay -> cancel/refund through the real HTTP API, security, idempotency and database. */
@RecordApplicationEvents
class BookingFlowIT extends IntegrationTestBase {

    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ApplicationEvents events;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired OutboxRelay outboxRelay;

    private String token;
    private ScheduleResponse schedule;
    private List<Long> economySeats;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        schedule = data.simpleSchedule();
        economySeats = data.seatIds(schedule.id(), SeatClass.ECONOMY);
    }

    private String bookingBody(String coupon, Long... seatIds) {
        StringBuilder passengers = new StringBuilder();
        for (int i = 0; i < seatIds.length; i++) {
            if (i > 0) passengers.append(',');
            passengers.append("""
                    {"fullName":"Passenger %d","age":30,"gender":"FEMALE","seatId":%d}""".formatted(i, seatIds[i]));
        }
        String couponJson = coupon == null ? "null" : "\"" + coupon + "\"";
        return """
                {"scheduleId":%d,"couponCode":%s,"passengers":[%s]}""".formatted(schedule.id(), couponJson, passengers);
    }

    private ResultActions createBooking(String key, String body) throws Exception {
        return mvc.perform(post("/api/bookings").header("Authorization", token).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions pay(long bookingId, String key, String body) throws Exception {
        return mvc.perform(post("/api/bookings/" + bookingId + "/payments").header("Authorization", token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long holdSeats(Long... seatIds) throws Exception {
        JsonNode booking = read(createBooking(newIdempotencyKey(), bookingBody(null, seatIds))
                .andExpect(status().isCreated()).andReturn());
        return booking.get("id").asLong();
    }

    private String seatStatus(long seatId) throws Exception {
        JsonNode seats = read(mvc.perform(get("/api/schedules/" + schedule.id() + "/seats")).andReturn());
        for (JsonNode s : seats) {
            if (s.get("id").asLong() == seatId) return s.get("status").asText();
        }
        throw new AssertionError("seat not found " + seatId);
    }

    @Test
    void holdSeatsCreatesBookingInSeatsHeldWithTenMinuteExpiry() throws Exception {
        MvcResult result = createBooking(newIdempotencyKey(),
                bookingBody("FLY10", economySeats.get(0), economySeats.get(1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("SEATS_HELD"))
                .andExpect(jsonPath("$.pnr").isNotEmpty())
                .andExpect(jsonPath("$.passengers.length()").value(2))
                .andExpect(jsonPath("$.totalAmount").value(9000.0))      // 2 x (5000 - 10%)
                .andExpect(jsonPath("$.holdExpiresAt").isNotEmpty())
                .andReturn();

        assertThat(seatStatus(economySeats.get(0))).isEqualTo("HELD");

        // The event sits in the outbox until the relay runs, then reaches the listeners exactly once.
        long bookingId = read(result).get("id").asLong();
        String pnr = read(result).get("pnr").asText();
        assertThat(events.stream(BookingEvent.class).filter(e -> pnr.equals(e.pnr()))).isEmpty();
        assertThat(outboxRepository.findByAggregateTypeAndAggregateIdOrderById(OutboxWriter.BOOKING, bookingId))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getEventType()).isEqualTo("BOOKING_SEATS_HELD");
                    assertThat(row.getPublishedAt()).isNull();
                });
        outboxRelay.relayPending();
        assertThat(events.stream(BookingEvent.class).filter(e -> pnr.equals(e.pnr())).map(BookingEvent::status))
                .containsExactly(BookingStatus.SEATS_HELD);
        outboxRelay.relayPending();
        assertThat(events.stream(BookingEvent.class).filter(e -> pnr.equals(e.pnr()))).hasSize(1);
        assertThat(outboxRepository.findByAggregateTypeAndAggregateIdOrderById(OutboxWriter.BOOKING, bookingId))
                .allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());
    }

    @Test
    void sameIdempotencyKeyReplaysResponseInsteadOfCreatingSecondBooking() throws Exception {
        String key = newIdempotencyKey();
        String body = bookingBody(null, economySeats.get(0));
        long first = read(createBooking(key, body).andExpect(status().isCreated()).andReturn()).get("id").asLong();

        createBooking(key, body)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(first));

        createBooking(key, bookingBody(null, economySeats.get(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(bookingRepository.findAll().stream()
                .filter(b -> b.getSchedule().getId().equals(schedule.id()))).hasSize(1);
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post("/api/bookings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(null, economySeats.get(0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void heldSeatCannotBeHeldAgain() throws Exception {
        holdSeats(economySeats.get(0));
        createBooking(newIdempotencyKey(), bookingBody(null, economySeats.get(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
    }

    @Test
    void successfulPaymentConfirmsBookingAndPaymentIsIdempotent() throws Exception {
        long bookingId = holdSeats(economySeats.get(0));
        String key = newIdempotencyKey();
        String body = "{\"method\":\"UPI\",\"upiId\":\"kunal@okaxis\"}";

        pay(bookingId, key, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.gatewayReference", startsWith("UPI-")));
        pay(bookingId, key, body)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(paymentRepository.findByBookingIdOrderByIdAsc(bookingId)).hasSize(1);   // charged once
        assertThat(seatStatus(economySeats.get(0))).isEqualTo("BOOKED");
        mvc.perform(get("/api/bookings/" + bookingId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.holdExpiresAt").doesNotExist());
    }

    @Test
    void payingAgainWithNewKeyIsAnInvalidTransition() throws Exception {
        long bookingId = holdSeats(economySeats.get(0));
        pay(bookingId, newIdempotencyKey(), "{\"method\":\"CARD\",\"cardToken\":\"tok_visa\"}")
                .andExpect(status().isOk());
        pay(bookingId, newIdempotencyKey(), "{\"method\":\"CARD\",\"cardToken\":\"tok_visa\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void declinedPaymentFailsBookingAndReleasesSeat() throws Exception {
        long bookingId = holdSeats(economySeats.get(0));
        pay(bookingId, newIdempotencyKey(), "{\"method\":\"UPI\",\"upiId\":\"fail@okaxis\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.bookingStatus").value("FAILED"));
        assertThat(seatStatus(economySeats.get(0))).isEqualTo("AVAILABLE");
    }

    @Test
    void cancelConfirmedBookingRefundsAndFreesSeat() throws Exception {
        long bookingId = holdSeats(economySeats.get(0));
        pay(bookingId, newIdempotencyKey(), "{\"method\":\"UPI\",\"upiId\":\"kunal@okaxis\"}").andExpect(status().isOk());

        mvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        assertThat(seatStatus(economySeats.get(0))).isEqualTo("AVAILABLE");
        assertThat(paymentRepository.findByBookingIdOrderByIdAsc(bookingId).get(0).getStatus().name())
                .isEqualTo("REFUNDED");
        mvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void cancelHeldBookingReleasesSeatWithoutRefund() throws Exception {
        long bookingId = holdSeats(economySeats.get(0));
        mvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(seatStatus(economySeats.get(0))).isEqualTo("AVAILABLE");
    }

    @Test
    void usersCannotSeeOrPayOthersBookings() throws Exception {
        long bookingId = holdSeats(economySeats.get(0));
        String otherUser = registerUser();
        mvc.perform(get("/api/bookings/" + bookingId).header("Authorization", otherUser))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/" + bookingId + "/payments").header("Authorization", otherUser)
                        .header("Idempotency-Key", newIdempotencyKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"UPI\",\"upiId\":\"x@okaxis\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsAreStructured() throws Exception {
        createBooking(newIdempotencyKey(), "{\"scheduleId\":null,\"passengers\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
        createBooking(newIdempotencyKey(), bookingBody("NOPE", economySeats.get(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUPON"));
    }

    @Test
    void listsMyBookings() throws Exception {
        holdSeats(economySeats.get(0));
        holdSeats(economySeats.get(1));
        mvc.perform(get("/api/bookings").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
