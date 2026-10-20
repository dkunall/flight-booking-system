package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.FlightSchedule;
import com.kunal.flightbooking.domain.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlightScheduleRepository extends JpaRepository<FlightSchedule, Long> {

    /** Single query with fetch joins: no N+1 when mapping search results. */
    @Query("""
            select fs from FlightSchedule fs
            join fetch fs.flight f
            join fetch f.airline a
            join fetch f.source src
            join fetch f.destination dst
            where src.code = :from and dst.code = :to
              and fs.departureTime >= :start and fs.departureTime < :end
              and fs.status = :status
            """)
    List<FlightSchedule> findForRoute(@Param("from") String from, @Param("to") String to,
                                      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                      @Param("status") ScheduleStatus status);

    @Query("""
            select fs from FlightSchedule fs
            join fetch fs.flight f
            join fetch f.airline a
            join fetch f.source src
            join fetch f.destination dst
            where src.code = :from and dst.code = :to
              and fs.departureTime >= :start and fs.departureTime < :end
              and fs.status = :status
              and a.code in :airlines
            """)
    List<FlightSchedule> findForRouteAndAirlines(@Param("from") String from, @Param("to") String to,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 @Param("status") ScheduleStatus status,
                                                 @Param("airlines") Collection<String> airlines);

    @Query("""
            select fs from FlightSchedule fs
            join fetch fs.flight f
            join fetch f.airline join fetch f.source join fetch f.destination
            where fs.id = :id
            """)
    Optional<FlightSchedule> findDetailedById(@Param("id") Long id);

    boolean existsByFlightIdAndDepartureTime(Long flightId, LocalDateTime departureTime);
}
