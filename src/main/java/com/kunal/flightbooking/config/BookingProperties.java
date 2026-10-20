package com.kunal.flightbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(@DefaultValue("10") int holdMinutes, @DefaultValue("9") int maxPassengers) {
}
