package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.Airline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long> {

    Optional<Airline> findByCode(String code);

    boolean existsByCode(String code);
}
