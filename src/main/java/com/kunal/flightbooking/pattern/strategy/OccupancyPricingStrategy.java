package com.kunal.flightbooking.pattern.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Occupancy pricing: the fuller the cabin, the higher the multiplier.
 * Tiers: under 50% full x1.00, 50-75% x1.15, 75-90% x1.30, 90%+ x1.50.
 */
@Component
@Order(2)
public class OccupancyPricingStrategy implements FareStrategy {

    @Override
    public BigDecimal apply(BigDecimal currentFare, FareContext context) {
        return currentFare.multiply(multiplierFor(context.occupancy()));
    }

    static BigDecimal multiplierFor(double occupancy) {
        if (occupancy >= 0.90) {
            return new BigDecimal("1.50");
        }
        if (occupancy >= 0.75) {
            return new BigDecimal("1.30");
        }
        if (occupancy >= 0.50) {
            return new BigDecimal("1.15");
        }
        return BigDecimal.ONE;
    }

    @Override
    public String name() {
        return "OCCUPANCY_PRICING";
    }
}
