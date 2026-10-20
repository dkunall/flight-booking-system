package com.kunal.flightbooking.pattern.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Step 1: start from the schedule's published base fare for the cabin. */
@Component
@Order(1)
public class BaseFareStrategy implements FareStrategy {

    @Override
    public BigDecimal apply(BigDecimal currentFare, FareContext context) {
        return context.baseFare();
    }

    @Override
    public String name() {
        return "BASE_FARE";
    }
}
