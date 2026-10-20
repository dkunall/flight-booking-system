package com.kunal.flightbooking.pattern.strategy;

import java.math.BigDecimal;

/**
 * Strategy for one pricing rule. Strategies are composed into a pipeline by {@link FareCalculator}: each
 * receives the fare produced by the previous step and returns an adjusted fare.
 */
public interface FareStrategy {

    BigDecimal apply(BigDecimal currentFare, FareContext context);

    String name();
}
