package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.service.RouteFinder;
import com.kunal.flightbooking.service.RouteFinder.Leg;
import com.kunal.flightbooking.service.RouteFinder.Objective;
import com.kunal.flightbooking.service.RouteFinder.Rules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RouteFinderTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 10);
    private final AtomicLong ids = new AtomicLong();

    private Leg leg(String from, String to, int depHour, int depMin, int minutes, int fare) {
        LocalDateTime dep = DAY.atTime(depHour, depMin);
        return new Leg(ids.incrementAndGet(), from, to, dep, dep.plusMinutes(minutes), BigDecimal.valueOf(fare));
    }

    private static Rules rules(int maxStops, int k, Objective objective) {
        return new Rules(maxStops, Duration.ofMinutes(45), Duration.ofHours(6), k, objective);
    }

    private static List<List<String>> codes(List<List<Leg>> paths) {
        return paths.stream().map(p -> p.stream().map(l -> l.from() + "-" + l.to()).toList()).toList();
    }

    private List<List<Leg>> find(List<Leg> legs, String from, String to, Rules rules) {
        return new RouteFinder(legs).find(from, to, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(), rules);
    }

    @Test
    void ordersDirectAndConnectingOptionsByTotalFare() {
        Leg direct = leg("DEL", "BLR", 7, 0, 165, 9000);
        Leg first = leg("DEL", "BOM", 6, 0, 130, 4000);
        Leg second = leg("BOM", "BLR", 11, 0, 100, 3500);

        List<List<Leg>> result = find(List.of(direct, first, second), "DEL", "BLR", rules(1, 5, Objective.PRICE));

        assertThat(result).containsExactly(List.of(first, second), List.of(direct));
    }

    @Test
    void durationObjectivePrefersTheDirectFlight() {
        Leg direct = leg("DEL", "BLR", 7, 0, 165, 9000);
        Leg first = leg("DEL", "BOM", 6, 0, 130, 4000);
        Leg second = leg("BOM", "BLR", 11, 0, 100, 3500);

        List<List<Leg>> result = find(List.of(direct, first, second), "DEL", "BLR", rules(1, 5, Objective.DURATION));

        assertThat(result.get(0)).containsExactly(direct);
    }

    @Test
    void respectsMinimumConnectionAndMaximumLayover() {
        Leg arrives0810 = leg("DEL", "BOM", 6, 0, 130, 4000);
        Leg tooTight = leg("BOM", "BLR", 8, 40, 100, 1000);   // 30 min after landing
        Leg tooLong = leg("BOM", "BLR", 14, 30, 100, 1000);   // 6h20 after landing
        Leg ok = leg("BOM", "BLR", 8, 55, 100, 3000);         // exactly 45 min

        List<List<Leg>> result = find(List.of(arrives0810, tooTight, tooLong, ok), "DEL", "BLR",
                rules(1, 5, Objective.PRICE));

        assertThat(result).containsExactly(List.of(arrives0810, ok));
    }

    @Test
    void capsStopsAndNeverRevisitsAnAirport() {
        List<Leg> legs = List.of(
                leg("AAA", "BBB", 6, 0, 60, 100),
                leg("BBB", "AAA", 8, 0, 60, 100),   // back to origin: never useful
                leg("BBB", "CCC", 8, 0, 60, 100),
                leg("CCC", "DDD", 10, 0, 60, 100),
                leg("DDD", "EEE", 12, 0, 60, 100));

        assertThat(codes(find(legs, "AAA", "EEE", rules(2, 5, Objective.PRICE)))).isEmpty();
        assertThat(codes(find(legs, "AAA", "DDD", rules(2, 5, Objective.PRICE))))
                .containsExactly(List.of("AAA-BBB", "BBB-CCC", "CCC-DDD"));
        assertThat(find(legs, "AAA", "DDD", rules(1, 5, Objective.PRICE))).isEmpty();
    }

    @Test
    void firstLegMustDepartOnTheRequestedDay() {
        Leg yesterday = new Leg(99, "DEL", "BLR", DAY.minusDays(1).atTime(23, 0), DAY.atTime(1, 0), BigDecimal.ONE);
        Leg today = leg("DEL", "BLR", 7, 0, 165, 9000);

        assertThat(find(List.of(yesterday, today), "DEL", "BLR", rules(0, 5, Objective.PRICE)))
                .containsExactly(List.of(today));
    }

    @Test
    void returnsAtMostKResults() {
        List<Leg> legs = List.of(
                leg("DEL", "BLR", 6, 0, 165, 5000),
                leg("DEL", "BLR", 9, 0, 165, 6000),
                leg("DEL", "BLR", 12, 0, 165, 7000),
                leg("DEL", "BLR", 15, 0, 165, 8000));

        List<List<Leg>> result = find(legs, "DEL", "BLR", rules(0, 2, Objective.PRICE));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get(0).fare()).isEqualByComparingTo("5000");
        assertThat(result.get(1).get(0).fare()).isEqualByComparingTo("6000");
    }
}
