package com.alejandro.controlvehicles.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One disagreement found by {@link DivergenceRules}: about a trip ({@code tripId}), a refuel ({@code refuelId})
 * or a stretch between two full-tank refuels (both refuel and vehicle).
 */
public record Divergence(DivergenceKind kind, Severity severity, LocalDate date, Long vehicleId, Long driverId,
                         Long tripId, Long refuelId, String detail) {

    public Divergence {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(severity);
        Objects.requireNonNull(date);
        Objects.requireNonNull(detail);
    }
}
