package com.kunal.flightbooking.exception;

import com.kunal.flightbooking.config.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;

/**
 * Builds RFC 7807 problem details. Besides the standard fields we always add a stable {@code code}
 * (clients switch on it, not on the message) and the request's correlation id.
 */
public final class Problems {

    private static final String TYPE_PREFIX = "https://flightbooking.dev/problems/";

    private Problems() {
    }

    public record FieldViolation(String field, String message) {
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }

    public static ProblemDetail withViolations(ProblemDetail problem, List<FieldViolation> violations) {
        problem.setProperty("fieldErrors", violations);
        return problem;
    }
}
