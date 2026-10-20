package com.kunal.flightbooking.integration;

import com.kunal.flightbooking.domain.*;
import com.kunal.flightbooking.dto.request.CreateBookingRequest;
import com.kunal.flightbooking.dto.request.PassengerRequest;
import com.kunal.flightbooking.dto.response.BookingResponse;
import com.kunal.flightbooking.exception.SeatUnavailableException;
import com.kunal.flightbooking.repository.BookingRepository;
import com.kunal.flightbooking.repository.SeatRepository;
import com.kunal.flightbooking.service.BookingService;
import com.kunal.flightbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N threads race to hold the same seat at the same instant. The PESSIMISTIC_WRITE lock must let exactly
 * one of them win; every other thread must fail and no seat may end up double-held.
 */
class SeatHoldConcurrencyIT extends IntegrationTestBase {

    private static final int THREADS = 12;

    @Autowired BookingService bookingService;
    @Autowired SeatRepository seatRepository;
    @Autowired BookingRepository bookingRepository;

    @RepeatedTest(3)
    void exactlyOneThreadCanHoldTheSameSeat() throws Exception {
        var schedule = data.simpleSchedule();
        Long seatId = data.seatIds(schedule.id(), SeatClass.ECONOMY).get(0);
        List<User> users = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            users.add(data.user(Role.USER));
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BookingResponse>> futures = new ArrayList<>();
        for (User user : users) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();                      // release all threads at once
                return bookingService.createBooking(user.getId(), new CreateBookingRequest(schedule.id(),
                        List.of(new PassengerRequest("Racer", 30, Gender.MALE, seatId)), null));
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();

        List<BookingResponse> winners = new ArrayList<>();
        List<Throwable> losers = new ArrayList<>();
        for (Future<BookingResponse> f : futures) {
            try {
                winners.add(f.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                losers.add(e.getCause());
            }
        }
        pool.shutdown();

        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(THREADS - 1);
        // Losers queued on the row lock, then re-read the committed HELD state and failed fast.
        assertThat(losers).allSatisfy(t -> assertThat(t).isInstanceOf(SeatUnavailableException.class));

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getBookingId()).isEqualTo(winners.get(0).id());
        long heldBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getSchedule().getId().equals(schedule.id()))
                .filter(b -> b.getStatus() == BookingStatus.SEATS_HELD)
                .count();
        assertThat(heldBookings).isEqualTo(1);
    }
}
