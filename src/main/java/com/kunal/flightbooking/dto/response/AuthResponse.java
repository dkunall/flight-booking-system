package com.kunal.flightbooking.dto.response;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, Long userId,
                           String email, String role) {
}
