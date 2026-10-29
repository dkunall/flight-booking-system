package com.kunal.flightbooking.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Finds the k cheapest (or fastest) itineraries with up to {@code maxStops} connections. No Spring, no
 * database: it gets a list of legs and returns lists of legs, so it is easy to test.
 *
 * <p>The graph is time-dependent, so nodes are flights (legs), not airports. Leg A connects to leg B when
 * B leaves A's arrival airport between {@code minConnection} and {@code maxLayover} after A lands. We run
 * a best-first search (Dijkstra with a priority queue of partial itineraries). Both cost functions only
 * grow as a path gets longer, so complete itineraries come off the queue in cost order. Allowing each leg
 * to be expanded at most {@code k} times gives the k best paths without exploring everything.
 */
public final class RouteFinder {

    public record Leg(long id, String from, String to, LocalDateTime departure, LocalDateTime arrival,
                      BigDecimal fare) {
    }

    public enum Objective { PRICE, DURATION }

    public record Rules(int maxStops, Duration minConnection, Duration maxLayover, int k, Objective objective) {
        public Rules {
            if (maxStops < 0 || k < 1) {
                throw new IllegalArgumentException("maxStops >= 0 and k >= 1 required");
            }
        }
    }

    private record Path(List<Leg> legs, BigDecimal fare, long minutes) {

        Leg last() {
            return legs.get(legs.size() - 1);
        }

        boolean visits(String airport) {
            return legs.get(0).from().equals(airport) || legs.stream().anyMatch(l -> l.to().equals(airport));
        }

        Path extend(Leg next) {
            List<Leg> copy = new ArrayList<>(legs);
            copy.add(next);
            return new Path(List.copyOf(copy), fare.add(next.fare()),
                    Duration.between(legs.get(0).departure(), next.arrival()).toMinutes());
        }

        static Path start(Leg leg) {
            return new Path(List.of(leg), leg.fare(), Duration.between(leg.departure(), leg.arrival()).toMinutes());
        }
    }

    private final Map<String, List<Leg>> departuresByAirport = new HashMap<>();

    public RouteFinder(Collection<Leg> legs) {
        for (Leg leg : legs) {
            departuresByAirport.computeIfAbsent(leg.from(), a -> new ArrayList<>()).add(leg);
        }
        departuresByAirport.values().forEach(list -> list.sort(Comparator.comparing(Leg::departure)));
    }

    /**
     * @param firstDepartureFrom earliest departure of the first leg (inclusive)
     * @param firstDepartureTo   latest departure of the first leg (exclusive)
     */
    public List<List<Leg>> find(String origin, String destination, LocalDateTime firstDepartureFrom,
                                LocalDateTime firstDepartureTo, Rules rules) {
        Comparator<Path> byCost = rules.objective() == Objective.PRICE
                ? Comparator.comparing(Path::fare).thenComparingLong(Path::minutes)
                : Comparator.comparingLong(Path::minutes).thenComparing(Path::fare);
        PriorityQueue<Path> queue = new PriorityQueue<>(byCost.thenComparing(p -> p.legs().get(0).departure()));
        for (Leg leg : departuresByAirport.getOrDefault(origin, List.of())) {
            if (!leg.departure().isBefore(firstDepartureFrom) && leg.departure().isBefore(firstDepartureTo)) {
                queue.add(Path.start(leg));
            }
        }

        Map<Long, Integer> expansions = new HashMap<>();
        List<List<Leg>> results = new ArrayList<>();
        while (!queue.isEmpty() && results.size() < rules.k()) {
            Path path = queue.poll();
            Leg last = path.last();
            if (last.to().equals(destination)) {
                results.add(path.legs());
                continue;
            }
            if (path.legs().size() > rules.maxStops() || expansions.merge(last.id(), 1, Integer::sum) > rules.k()) {
                continue;
            }
            LocalDateTime earliest = last.arrival().plus(rules.minConnection());
            LocalDateTime latest = last.arrival().plus(rules.maxLayover());
            for (Leg next : departuresByAirport.getOrDefault(last.to(), List.of())) {
                if (next.departure().isAfter(latest)) {
                    break;   // sorted by departure
                }
                if (!next.departure().isBefore(earliest) && !path.visits(next.to())) {
                    queue.add(path.extend(next));
                }
            }
        }
        return results;
    }
}
