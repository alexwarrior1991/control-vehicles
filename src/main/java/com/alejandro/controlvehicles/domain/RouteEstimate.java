package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** The route Google computed between the origin and the destination of a trip. */
public record RouteEstimate(int distanceMeters, int durationSeconds, TollStatus tollStatus,
                            BigDecimal tollEstimate, Instant computedAt) {

    public RouteEstimate {
        if (distanceMeters < 0 || durationSeconds < 0) {
            throw new IllegalArgumentException("distance and duration cannot be negative");
        }
        Objects.requireNonNull(tollStatus, "toll status is required");
        if (tollStatus != TollStatus.PRICED) {
            tollEstimate = null;
        }
        Objects.requireNonNull(computedAt, "computedAt is required");
    }

    /** Exact kilometres (three decimals): costs and sums are made with them, only the screens round. */
    public BigDecimal distanceKm() {
        return BigDecimal.valueOf(distanceMeters).divide(BigDecimal.valueOf(1000), 3, RoundingMode.UNNECESSARY);
    }

    public Duration duration() {
        return Duration.ofSeconds(durationSeconds);
    }
}
