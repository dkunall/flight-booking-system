package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.Payment;
import com.kunal.flightbooking.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    List<Payment> findByBookingIdOrderByIdAsc(Long bookingId);
}
