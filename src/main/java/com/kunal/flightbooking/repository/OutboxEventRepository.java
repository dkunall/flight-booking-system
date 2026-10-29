package com.kunal.flightbooking.repository;

import com.kunal.flightbooking.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** SKIP LOCKED lets several app instances relay in parallel without handing out the same row twice. */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL AND attempts < :maxAttempts
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("maxAttempts") int maxAttempts, @Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderById(String aggregateType, Long aggregateId);
}
