package com.kunal.flightbooking.pattern.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A discount rule: either a percentage (optionally capped) or a flat amount. */
public record Coupon(String code, BigDecimal percentOff, BigDecimal maxDiscount, BigDecimal flatOff) {

    public static Coupon percent(String code, String percent, String cap) {
        return new Coupon(code, new BigDecimal(percent), new BigDecimal(cap), null);
    }

    public static Coupon flat(String code, String amount) {
        return new Coupon(code, null, null, new BigDecimal(amount));
    }

    public BigDecimal discountOn(BigDecimal fare) {
        if (flatOff != null) {
            return flatOff.min(fare);
        }
        BigDecimal discount = fare.multiply(percentOff).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return maxDiscount == null ? discount : discount.min(maxDiscount);
    }
}
