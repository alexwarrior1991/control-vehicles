package com.alejandro.controlvehicles.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * When a difference becomes a divergence. A distance or a duration needs to pass both its percentage and its
 * absolute minimum, so that short trips do not raise noise. {@code unrecordedMinKm} is how many kilometres between
 * a trip's end and the next start are still a reading error rather than use.
 */
public record DivergenceThresholds(int distancePercent, int distanceMinKm, int durationPercent,
                                   int durationMinMinutes, int consumptionPercent, BigDecimal tollToleranceEur,
                                   int unrecordedMinKm) {

    public static final DivergenceThresholds DEFAULTS =
            new DivergenceThresholds(10, 5, 30, 15, 15, new BigDecimal("1.00"), 3);

    public DivergenceThresholds {
        if (distancePercent < 0 || distanceMinKm < 0 || durationPercent < 0 || durationMinMinutes < 0
                || consumptionPercent < 0 || unrecordedMinKm < 0) {
            throw new IllegalArgumentException("thresholds cannot be negative");
        }
        Objects.requireNonNull(tollToleranceEur);
        if (tollToleranceEur.signum() < 0) {
            throw new IllegalArgumentException("thresholds cannot be negative");
        }
    }
}
