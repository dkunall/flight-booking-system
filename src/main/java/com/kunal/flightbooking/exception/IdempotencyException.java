package com.kunal.flightbooking.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyException extends BusinessException {

    private IdempotencyException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static IdempotencyException keyReusedWithDifferentPayload(String key) {
        return new IdempotencyException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key '" + key + "' was already used with a different request body");
    }

    public static IdempotencyException requestInProgress(String key) {
        return new IdempotencyException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS",
                "A request with Idempotency-Key '" + key + "' is still being processed");
    }

    public static IdempotencyException invalidKey() {
        return new IdempotencyException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key header must be 8-100 characters of [A-Za-z0-9-_]");
    }
}
