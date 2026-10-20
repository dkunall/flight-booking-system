package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserIdAndOperation(String idempotencyKey, Long userId,
                                                                           String operation);
}
