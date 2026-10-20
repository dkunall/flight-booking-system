package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.Airport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long> {

    Optional<Airport> findByCode(String code);

    boolean existsByCode(String code);
}
