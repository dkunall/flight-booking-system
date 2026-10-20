package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.IdempotencyRecord;
import com.kunal.flightbooking.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persistence for idempotency records. Each method runs in its own transaction (REQUIRES_NEW) so the
 * reservation is visible to concurrent requests before the business operation starts, and survives
 * independently of the business transaction's outcome.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private final IdempotencyRecordRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyRecord> find(String key, Long userId, String operation) {
        return repository.findByIdempotencyKeyAndUserIdAndOperation(key, userId, operation);
    }

    /** Inserts an IN_PROGRESS row; the unique constraint makes concurrent duplicates fail here. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long reserve(String key, Long userId, String operation, String requestHash) {
        return repository.saveAndFlush(new IdempotencyRecord(key, userId, operation, requestHash)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long recordId, int status, String body) {
        repository.findById(recordId).ifPresent(r -> r.complete(status, body));
    }

    /** Business call failed: free the key so the client may retry with it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long recordId) {
        repository.deleteById(recordId);
    }
}
