package com.alejandro.controlvehicles.service;

import com.alejandro.controlvehicles.domain.Divergence;
import com.alejandro.controlvehicles.domain.Driver;
import com.alejandro.controlvehicles.domain.Place;
import com.alejandro.controlvehicles.domain.Severity;
import com.alejandro.controlvehicles.domain.Trip;
import com.alejandro.controlvehicles.domain.Vehicle;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** A trip with everything a list shows: names, the odometer distance and its divergences. */
public record TripRow(Trip trip, Vehicle vehicle, Driver driver, Place origin, Place destination, Integer odometerKm,
                      boolean pending, List<Divergence> divergences) {

    public BigDecimal googleKm() {
        return trip.route() == null ? null : trip.route().distanceKm();
    }

    /** Odometer km minus Google km, when both are known. */
    public BigDecimal gapKm() {
        BigDecimal google = googleKm();
        return odometerKm == null || google == null ? null : BigDecimal.valueOf(odometerKm).subtract(google);
    }

    public Optional<Severity> worstSeverity() {
        return divergences.stream().map(Divergence::severity).max(Comparator.naturalOrder());
    }
}
