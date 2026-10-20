package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.Seat;
import com.kunal.flightbooking.domain.SeatClass;
import com.kunal.flightbooking.domain.SeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * SELECT ... FOR UPDATE on the requested seats. Rows are always locked in primary-key order so two
     * bookings requesting overlapping seat sets cannot deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select s from Seat s where s.id in :ids order by s.id")
    List<Seat> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.bookingId = :bookingId order by s.id")
    List<Seat> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    @Query("select s from Seat s where s.schedule.id = :scheduleId order by s.id")
    List<Seat> findByScheduleId(@Param("scheduleId") Long scheduleId);

    /** Aggregated availability for many schedules in one round trip: rows of [scheduleId, seatClass, count]. */
    @Query("""
            select s.schedule.id, s.seatClass, count(s) from Seat s
            where s.schedule.id in :scheduleIds and s.status = :status
            group by s.schedule.id, s.seatClass
            """)
    List<Object[]> countBySchedulesAndStatus(@Param("scheduleIds") Collection<Long> scheduleIds,
                                             @Param("status") SeatStatus status);

    @Query("""
            select count(s) from Seat s
            where s.schedule.id = :scheduleId and s.seatClass = :seatClass and s.status = :status
            """)
    long countByScheduleAndClassAndStatus(@Param("scheduleId") Long scheduleId,
                                          @Param("seatClass") SeatClass seatClass,
                                          @Param("status") SeatStatus status);
}
