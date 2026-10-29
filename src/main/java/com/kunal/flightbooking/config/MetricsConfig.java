package com.kunal.flightbooking.config;

import com.kunal.flightbooking.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    /** Unpublished outbox rows. Should hover near zero; a climb means the relay is stuck or listeners fail. */
    @Bean
    MeterBinder outboxPendingGauge(OutboxEventRepository outbox) {
        return registry -> Gauge.builder("outbox.pending", outbox, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events not yet relayed")
                .register(registry);
    }
}
