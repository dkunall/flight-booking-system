package com.kunal.flightbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.flightbooking.domain.IdempotencyRecord;
import com.kunal.flightbooking.domain.IdempotencyStatus;
import com.kunal.flightbooking.exception.IdempotencyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Executes a mutating operation at most once per (Idempotency-Key, user, operation).
 * <ul>
 *   <li>same key + same body, completed: the stored response is replayed (no second booking/charge)</li>
 *   <li>same key + different body: 422</li>
 *   <li>same key while the first request is still running: 409</li>
 *   <li>operation threw: the key is released so the client can retry</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public record Result<T>(T body, boolean replayed) {
    }

    public <T> Result<T> execute(String key, Long userId, String operation, Object request, int successStatus,
                                 Class<T> responseType, Supplier<T> action) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw IdempotencyException.invalidKey();
        }
        String requestHash = hash(request);

        var existing = store.find(key, userId, operation);
        if (existing.isPresent()) {
            return replay(existing.get(), key, requestHash, responseType);
        }

        Long recordId;
        try {
            recordId = store.reserve(key, userId, operation, requestHash);
        } catch (DataIntegrityViolationException race) {
            // Another request with the same key inserted first.
            return store.find(key, userId, operation)
                    .map(r -> replay(r, key, requestHash, responseType))
                    .orElseThrow(() -> IdempotencyException.requestInProgress(key));
        }

        T response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            store.release(recordId);
            throw e;
        }
        store.complete(recordId, successStatus, toJson(response));
        return new Result<>(response, false);
    }

    private <T> Result<T> replay(IdempotencyRecord record, String key, String requestHash, Class<T> type) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw IdempotencyException.keyReusedWithDifferentPayload(key);
        }
        if (record.getStatus() != IdempotencyStatus.COMPLETED) {
            throw IdempotencyException.requestInProgress(key);
        }
        log.info("Replaying stored response for Idempotency-Key {}", key);
        try {
            return new Result<>(objectMapper.readValue(record.getResponseBody(), type), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt idempotency record " + record.getId(), e);
        }
    }

    private String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(toJson(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value.getClass().getSimpleName(), e);
        }
    }
}
