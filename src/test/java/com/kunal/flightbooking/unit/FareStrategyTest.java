package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.exception.InvalidCouponException;
import com.kunal.flightbooking.pattern.strategy.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareStrategyTest {

    private final FareCalculator calculator = FareCalculator.of(
            new BaseFareStrategy(), new OccupancyPricingStrategy(), new CouponDiscountStrategy());

    private static FareContext ctx(String base, int total, int available, String coupon) {
        return new FareContext(SeatClass.ECONOMY, new BigDecimal(base), total, available, coupon);
    }

    @Test
    void baseFareStrategyIgnoresIncomingFareAndUsesScheduleBaseFare() {
        assertThat(new BaseFareStrategy().apply(new BigDecimal("999"), ctx("5000", 100, 100, null)))
                .isEqualByComparingTo("5000");
    }

    @ParameterizedTest(name = "{1} of 100 seats left -> {2}")
    @CsvSource({
            "5000, 100, 5000.00",   // 0% occupied
            "5000, 51, 5000.00",    // 49%
            "5000, 50, 5750.00",    // 50% -> x1.15
            "5000, 25, 6500.00",    // 75% -> x1.30
            "5000, 10, 7500.00",    // 90% -> x1.50
            "5000, 1, 7500.00"})
    void occupancyTiersScaleFare(String base, int available, String expected) {
        assertThat(calculator.calculate(ctx(base, 100, available, null))).isEqualByComparingTo(expected);
    }

    @Test
    void percentageCouponIsCapped() {
        // 10% of 4000 = 400 (under the 500 cap)
        assertThat(calculator.calculate(ctx("4000", 100, 100, "FLY10"))).isEqualByComparingTo("3600.00");
        // 10% of 9000 = 900, capped at 500
        assertThat(calculator.calculate(ctx("9000", 100, 100, "fly10"))).isEqualByComparingTo("8500.00");
    }

    @Test
    void flatCouponNeverMakesFareNegative() {
        assertThat(calculator.calculate(ctx("4000", 100, 100, "FLAT300"))).isEqualByComparingTo("3700.00");
        assertThat(calculator.calculate(ctx("200", 100, 100, "FLAT300"))).isEqualByComparingTo("0.00");
    }

    @Test
    void strategiesComposeInOrder_dynamicPricingBeforeCoupon() {
        // 5000 x 1.30 (80% full) = 6500, then FLY10 -> 650 capped at 500 -> 6000
        assertThat(calculator.calculate(ctx("5000", 100, 20, "FLY10"))).isEqualByComparingTo("6000.00");
        assertThat(calculator.pipeline()).containsExactly("BASE_FARE", "OCCUPANCY_PRICING", "COUPON_DISCOUNT");
    }

    @Test
    void pipelineIsExtensibleWithoutModifyingCalculator() {
        FareStrategy festiveSurcharge = new FareStrategy() {
            public BigDecimal apply(BigDecimal fare, FareContext c) { return fare.add(new BigDecimal("199")); }
            public String name() { return "FESTIVE"; }
        };
        FareCalculator custom = FareCalculator.of(new BaseFareStrategy(), festiveSurcharge);
        assertThat(custom.calculate(ctx("1000", 10, 10, null))).isEqualByComparingTo("1199.00");
    }

    @Test
    void unknownCouponIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(ctx("4000", 100, 100, "BOGUS")))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    void occupancyHandlesEmptyCabin() {
        assertThat(ctx("1", 0, 0, null).occupancy()).isZero();
    }
}
