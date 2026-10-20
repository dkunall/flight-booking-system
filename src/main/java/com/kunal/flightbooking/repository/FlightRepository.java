package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    boolean existsByFlightNumber(String flightNumber);

    @Query("""
            select f from Flight f
            join fetch f.airline join fetch f.source join fetch f.destination
            where f.id = :id
            """)
    Optional<Flight> findDetailedById(@Param("id") Long id);
}
