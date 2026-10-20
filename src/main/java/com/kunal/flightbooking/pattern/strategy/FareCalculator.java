package com.kunal.flightbooking.pattern.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Composes {@link FareStrategy} beans into an ordered pipeline (order taken from {@code @Order}).
 * Adding a new rule (e.g. festive surcharge) means adding a bean, not editing this class (Open/Closed).
 */
@Component
public class FareCalculator {

    private final List<FareStrategy> strategies;

    public FareCalculator(List<FareStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public static FareCalculator of(FareStrategy... strategies) {
        return new FareCalculator(List.of(strategies));
    }

    /** Fare for one passenger, rounded to paise, never negative. */
    public BigDecimal calculate(FareContext context) {
        BigDecimal fare = BigDecimal.ZERO;
        for (FareStrategy strategy : strategies) {
            fare = strategy.apply(fare, context);
        }
        return fare.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public List<String> pipeline() {
        return strategies.stream().map(FareStrategy::name).toList();
    }
}
