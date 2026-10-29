package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.Booking;
import com.kunal.flightbooking.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Serialises pay / cancel / expire on the same booking. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("select b from Booking b where b.user.id = :userId order by b.createdAt desc")
    List<Booking> findByUserId(@Param("userId") Long userId);

    @Query("select b.id from Booking b where b.status = :status and b.holdExpiresAt <= :now order by b.holdExpiresAt")
    List<Long> findIdsWithExpiredHold(@Param("status") BookingStatus status, @Param("now") LocalDateTime now);

    /**
     * Revenue per route from confirmed bookings: rows of [source, destination, bookings, passengers, revenue].
     * Native SQL so the joins and aggregation are explicit.
     */
    @Query(value = """
            SELECT src.code AS source_code,
                   dst.code AS destination_code,
                   COUNT(DISTINCT b.id) AS bookings,
                   COUNT(p.id) AS passengers,
                   COALESCE(SUM(p.fare), 0) AS revenue
            FROM bookings b
            JOIN flight_schedules fs ON fs.id = b.schedule_id
            JOIN flights f ON f.id = fs.flight_id
            JOIN airports src ON src.id = f.source_airport_id
            JOIN airports dst ON dst.id = f.destination_airport_id
            JOIN passengers p ON p.booking_id = b.id
            WHERE b.status = 'CONFIRMED'
            GROUP BY src.code, dst.code
            ORDER BY revenue DESC, src.code, dst.code
            """, nativeQuery = true)
    List<Object[]> routeRevenueReport();
}
