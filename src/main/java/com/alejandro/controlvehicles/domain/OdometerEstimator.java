package com.alejandro.controlvehicles.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The odometer a vehicle most likely had at a moment: the last reading known at or before it (a trip's start, a
 * trip's noted end already reached, a refuel whose odometer was read), or the vehicle's initial odometer. Used to
 * propose the start of a new trip and to fill the odometer of an imported refuel the statement does not tell.
 */
public final class OdometerEstimator {

    private OdometerEstimator() {
    }

    public static int estimate(Vehicle vehicle, List<Trip> tripsOfVehicle, List<Refuel> refuelsOfVehicle,
                               LocalDateTime at) {
        int best = vehicle.initialOdometerKm();
        for (Trip trip : tripsOfVehicle) {
            if (trip.startAt().isAfter(at)) {
                continue;
            }
            best = Math.max(best, trip.startOdometerKm());
            if (trip.endOdometerKm() != null && !trip.endAt().isAfter(at)) {
                best = Math.max(best, trip.endOdometerKm());
            }
        }
        for (Refuel refuel : refuelsOfVehicle) {
            if (!refuel.odometerEstimated() && !refuel.refuelAt().isAfter(at)) {
                best = Math.max(best, refuel.odometerKm());
            }
        }
        return best;
    }
}
