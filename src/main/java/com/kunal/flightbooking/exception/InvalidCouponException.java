package com.kunal.flightbooking.exception;

import org.springframework.http.HttpStatus;

public class InvalidCouponException extends BusinessException {
    public InvalidCouponException(String code) {
        super(HttpStatus.BAD_REQUEST, "INVALID_COUPON", "Unknown coupon code: " + code);
    }
}
