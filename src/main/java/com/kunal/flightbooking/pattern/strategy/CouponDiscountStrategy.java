package com.kunal.flightbooking.pattern.strategy;

import com.kunal.flightbooking.exception.InvalidCouponException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Step 3: apply a coupon (per passenger fare). Unknown coupons are rejected rather than silently ignored. */
@Component
@Order(3)
public class CouponDiscountStrategy implements FareStrategy {

    private final Map<String, Coupon> coupons;

    public CouponDiscountStrategy() {
        this(Map.of(
                "FLY10", Coupon.percent("FLY10", "10", "500"),
                "FIRSTFLIGHT", Coupon.percent("FIRSTFLIGHT", "15", "1000"),
                "FLAT300", Coupon.flat("FLAT300", "300")));
    }

    public CouponDiscountStrategy(Map<String, Coupon> coupons) {
        this.coupons = Map.copyOf(coupons);
    }

    @Override
    public BigDecimal apply(BigDecimal currentFare, FareContext context) {
        if (!context.hasCoupon()) {
            return currentFare;
        }
        Coupon coupon = find(context.couponCode())
                .orElseThrow(() -> new InvalidCouponException(context.couponCode()));
        return currentFare.subtract(coupon.discountOn(currentFare));
    }

    public Optional<Coupon> find(String code) {
        return Optional.ofNullable(coupons.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public String name() {
        return "COUPON_DISCOUNT";
    }
}
